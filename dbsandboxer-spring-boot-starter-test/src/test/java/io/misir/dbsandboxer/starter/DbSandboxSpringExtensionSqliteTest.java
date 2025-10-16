package io.misir.dbsandboxer.starter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Integration tests verifying that {@link DbSandboxSpringExtension} manages SQLite databases using
 * real Spring application contexts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ContextConfiguration(classes = DbSandboxSpringExtensionSqliteTest.SqliteTestConfig.class)
@ExtendWith(SpringExtension.class)
@EnableDbSandboxer(sqliteTemplateFile = "templates/prebuilt.db")
class DbSandboxSpringExtensionSqliteTest {

    private static final Path ROOT_DIRECTORY = createRootDirectory();
    private static final Path DATABASE_FILE = ROOT_DIRECTORY.resolve("workspace/app.db");
    private static final Path TEMPLATE_FILE = DATABASE_FILE.getParent().resolve("templates/prebuilt.db");

    static {
        initializePrimaryDatabase();
    }

    @Autowired
    private DataSource dataSource;

    @Test
    @Order(1)
    void extensionCreatesTemplateAndCopiesDatabaseForTests() throws Exception {
        assertTrue(Files.exists(TEMPLATE_FILE), "SQLite template should be created by the extension");
        assertTrue(Files.exists(DATABASE_FILE), "Primary SQLite database should exist");
        assertEquals(2, countUsers(), "Initial sandbox copy should contain seeded users");

        insertUser("Charlie", "charlie@example.com");
        assertEquals(3, countUsers(), "Inserting a user mutates only the current sandbox copy");

        try (Connection templateConnection =
                DriverManager.getConnection("jdbc:sqlite:" + TEMPLATE_FILE.toString());
                Statement stmt = templateConnection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "Template database remains untouched by test mutations");
        }
    }

    @Test
    @Order(2)
    void extensionRestoresDatabaseBetweenTests() throws Exception {
        assertEquals(2, countUsers(), "Sandbox should be rebuilt from template before each test");
        assertTrue(Files.exists(TEMPLATE_FILE), "Template file should still be present");
    }

    @AfterAll
    static void cleanUpFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(ROOT_DIRECTORY)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup. Test directories are unique per run.
                }
            });
        }
    }

    private static void initializePrimaryDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(DATABASE_FILE.getParent());
            try (Connection connection =
                    DriverManager.getConnection("jdbc:sqlite:" + DATABASE_FILE.toString());
                    Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE IF EXISTS users");
                statement.executeUpdate(
                        "CREATE TABLE users (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "name TEXT NOT NULL, " +
                                "email TEXT NOT NULL UNIQUE)");
                statement.executeUpdate(
                        "INSERT INTO users (name, email) VALUES " +
                                "('Alice', 'alice@example.com')," +
                                "('Bob', 'bob@example.com')");
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Path createRootDirectory() {
        try {
            return Files.createTempDirectory("dbsandboxer-spring-sqlite-test");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private int countUsers() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private void insertUser(String name, String email) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO users (name, email) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.executeUpdate();
        }
    }

    @Configuration
    static class SqliteTestConfig {

        @Bean
        DataSource dataSource() {
            org.sqlite.SQLiteDataSource dataSource = new org.sqlite.SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + DATABASE_FILE.toString());
            return dataSource;
        }
    }
}
