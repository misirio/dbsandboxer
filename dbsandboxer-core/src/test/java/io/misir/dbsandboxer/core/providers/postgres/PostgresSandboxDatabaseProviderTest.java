package io.misir.dbsandboxer.core.providers.postgres;

import static org.assertj.core.api.Assertions.*;

import io.misir.dbsandboxer.core.api.SandboxException;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DisplayName("PostgresSandboxDatabaseProvider Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresSandboxDatabaseProviderTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresSandboxDatabaseProvider provider;
    private Connection adminConnection;
    private static final String PRIMARY_DB = "public";
    private static final String TEMPLATE_NAME = "template_database";

    @BeforeAll
    void setUp() throws Exception {
        String host = postgres.getHost();
        int port = postgres.getMappedPort(5432);

        adminConnection =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement stmt = adminConnection.createStatement()) {
            // First alter template_database to not be a template before dropping
            stmt.execute("DROP DATABASE IF EXISTS " + PRIMARY_DB);
            stmt.execute("CREATE DATABASE " + PRIMARY_DB);
        }

        try (Connection appDb =
                        DriverManager.getConnection(
                                "jdbc:postgresql://" + host + ":" + port + "/" + PRIMARY_DB,
                                postgres.getUsername(),
                                postgres.getPassword());
                Statement stmt = appDb.createStatement()) {

            stmt.execute(
                    """
                        CREATE TABLE users (
                            id SERIAL PRIMARY KEY,
                            name VARCHAR(100),
                            email VARCHAR(100) UNIQUE
                        )
                    """);

            stmt.execute(
                    """
                        INSERT INTO users (name, email) VALUES
                        ('Alice', 'alice@example.com'),
                        ('Bob', 'bob@example.com')
                    """);

            stmt.execute(
                    """
                        CREATE TABLE products (
                            id SERIAL PRIMARY KEY,
                            name VARCHAR(100),
                            price DECIMAL(10, 2)
                        )
                    """);
        }

        provider =
                new PostgresSandboxDatabaseProvider(
                        host,
                        port,
                        postgres.getDatabaseName(),
                        postgres.getUsername(),
                        postgres.getPassword(),
                        PRIMARY_DB,
                        TEMPLATE_NAME);
    }

    @Nested
    @DisplayName("prepareSandbox Tests")
    class PrepareSandboxTests {

        @Test
        @DisplayName("Should create template database on first call")
        void shouldCreateTemplateDatabaseOnFirstCall() throws SQLException {
            provider.prepareSandbox();

            assertThat(templateExists()).isTrue();
            assertThat(databaseExists(PRIMARY_DB)).isTrue();
        }

        @Test
        @DisplayName("Should be idempotent")
        void shouldBeIdempotent() throws SQLException {
            provider.prepareSandbox();
            provider.prepareSandbox();
            provider.prepareSandbox();

            assertThat(templateExists()).isTrue();
        }

        @Test
        @DisplayName("Should preserve template even after primary DB changes")
        void shouldPreserveTemplateAfterPrimaryDbChanges() throws SQLException {
            provider.prepareSandbox();

            modifyPrimaryDatabase();

            provider.prepareSandbox();

            assertThat(getTableRowCount("template_database", "users")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should be thread-safe")
        void shouldBeThreadSafe() throws Exception {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger exceptionCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(
                        () -> {
                            try {
                                startLatch.await();
                                provider.prepareSandbox();
                                successCount.incrementAndGet();
                            } catch (Exception e) {
                                exceptionCount.incrementAndGet();
                            } finally {
                                completionLatch.countDown();
                            }
                        });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(exceptionCount.get()).isZero();
            assertThat(templateExists()).isTrue();
        }
    }

    @Nested
    @DisplayName("rebuildSandbox Tests")
    class RebuildSandboxTests {

        @BeforeEach
        void prepareTemplate() {
            provider.prepareSandbox();
        }

        @Test
        @DisplayName("Should drop and recreate primary database")
        void shouldDropAndRecreatePrimaryDatabase() throws SQLException {
            modifyPrimaryDatabase();

            provider.rebuildSandbox();

            assertThat(getTableRowCount(PRIMARY_DB, "users")).isEqualTo(2);
            assertThat(getTableRowCount(PRIMARY_DB, "products")).isEqualTo(0);
        }

        @Test
        @DisplayName("Should restore database to template state")
        void shouldRestoreDatabaseToTemplateState() throws SQLException {
            insertTestData();
            assertThat(getTableRowCount(PRIMARY_DB, "users")).isGreaterThan(2);

            provider.rebuildSandbox();

            assertThat(getTableRowCount(PRIMARY_DB, "users")).isEqualTo(2);
            List<String> emails = getUserEmails();
            assertThat(emails).containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
        }

        @Test
        @DisplayName("Should terminate existing connections")
        void shouldTerminateExistingConnections() throws SQLException, InterruptedException {
            Connection conn1 = createAppConnection();
            Connection conn2 = createAppConnection();

            provider.rebuildSandbox();

            // Give connections time to be terminated
            Thread.sleep(100);

            // Check if connections are closed or invalid
            boolean conn1Invalid;
            boolean conn2Invalid;

            try {
                conn1Invalid = conn1.isClosed() || !conn1.isValid(1);
            } catch (SQLException e) {
                conn1Invalid = true;
            }

            try {
                conn2Invalid = conn2.isClosed() || !conn2.isValid(1);
            } catch (SQLException e) {
                conn2Invalid = true;
            }

            assertThat(conn1Invalid).isTrue();
            assertThat(conn2Invalid).isTrue();
        }

        @Test
        @DisplayName("Should throw SandboxException on database error")
        void shouldThrowSandboxExceptionOnDatabaseError() {
            PostgresSandboxDatabaseProvider badProvider =
                    new PostgresSandboxDatabaseProvider(
                            "invalid-host",
                            5432,
                            "admin",
                            "user",
                            "pass",
                            "primary",
                            TEMPLATE_NAME);

            assertThatThrownBy(badProvider::rebuildSandbox)
                    .isInstanceOf(SandboxException.class)
                    .hasCauseInstanceOf(SQLException.class);
        }
    }

    @Nested
    @DisplayName("Integration Workflow Tests")
    class IntegrationWorkflowTests {

        @Test
        @DisplayName("Should support complete test workflow")
        void shouldSupportCompleteTestWorkflow() throws SQLException {
            provider.prepareSandbox();

            provider.rebuildSandbox();
            insertTestData();
            assertThat(getTableRowCount(PRIMARY_DB, "users")).isGreaterThan(2);

            provider.rebuildSandbox();
            assertThat(getTableRowCount(PRIMARY_DB, "users")).isEqualTo(2);

            provider.rebuildSandbox();
            assertThat(getTableRowCount(PRIMARY_DB, "users")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should maintain template across multiple JVM instances simulation")
        void shouldMaintainTemplateAcrossMultipleJvmInstancesSimulation() throws Exception {
            provider.prepareSandbox();
            assertThat(templateExists()).isTrue();

            // Reset the static flag to simulate a new JVM instance
            Field templateReadyField =
                    PostgresSandboxDatabaseProvider.class.getDeclaredField("TEMPLATE_READY");
            templateReadyField.setAccessible(true);
            AtomicBoolean templateReady = (AtomicBoolean) templateReadyField.get(null);
            templateReady.set(false);

            PostgresSandboxDatabaseProvider newProvider =
                    new PostgresSandboxDatabaseProvider(
                            postgres.getHost(),
                            postgres.getMappedPort(5432),
                            postgres.getDatabaseName(),
                            postgres.getUsername(),
                            postgres.getPassword(),
                            PRIMARY_DB,
                            TEMPLATE_NAME);

            newProvider.prepareSandbox();

            assertThat(templateExists()).isTrue();
        }

        @Test
        @DisplayName("Should handle rapid rebuild cycles")
        void shouldHandleRapidRebuildCycles() throws SQLException {
            provider.prepareSandbox();

            for (int i = 0; i < 10; i++) {
                provider.rebuildSandbox();
                insertTestData();
                assertThat(getTableRowCount(PRIMARY_DB, "users")).isGreaterThan(2);
            }

            provider.rebuildSandbox();
            assertThat(getTableRowCount(PRIMARY_DB, "users")).isEqualTo(2);
        }
    }

    private boolean templateExists() throws SQLException {
        try (PreparedStatement ps =
                adminConnection.prepareStatement(
                        "SELECT 1 FROM pg_database WHERE datname = ? AND datistemplate")) {
            ps.setString(1, "template_database");
            return ps.executeQuery().next();
        }
    }

    private boolean databaseExists(String dbName) throws SQLException {
        try (PreparedStatement ps =
                adminConnection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, dbName);
            return ps.executeQuery().next();
        }
    }

    private void modifyPrimaryDatabase() throws SQLException {
        try (Connection appDb = createAppConnection();
                Statement stmt = appDb.createStatement()) {
            stmt.execute(
                    "INSERT INTO users (name, email) VALUES ('Charlie', 'charlie@example.com')");
            stmt.execute("DROP TABLE IF EXISTS products");
        }
    }

    private void insertTestData() throws SQLException {
        try (Connection appDb = createAppConnection();
                Statement stmt = appDb.createStatement()) {
            stmt.execute("INSERT INTO users (name, email) VALUES ('Test1', 'test1@example.com')");
            stmt.execute("INSERT INTO users (name, email) VALUES ('Test2', 'test2@example.com')");
        }
    }

    private int getTableRowCount(String database, String table) throws SQLException {
        String url =
                "jdbc:postgresql://"
                        + postgres.getHost()
                        + ":"
                        + postgres.getMappedPort(5432)
                        + "/"
                        + database;
        try (Connection conn =
                        DriverManager.getConnection(
                                url, postgres.getUsername(), postgres.getPassword());
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<String> getUserEmails() throws SQLException {
        List<String> emails = new ArrayList<>();
        try (Connection appDb = createAppConnection();
                Statement stmt = appDb.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT email FROM users ORDER BY email")) {
            while (rs.next()) {
                emails.add(rs.getString(1));
            }
        }
        return emails;
    }

    private Connection createAppConnection() throws SQLException {
        String url =
                "jdbc:postgresql://"
                        + postgres.getHost()
                        + ":"
                        + postgres.getMappedPort(5432)
                        + "/"
                        + PRIMARY_DB;
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }
}
