package io.misir.dbsandboxer.examples.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.misir.dbsandboxer.examples.boot.domain.Product;
import io.misir.dbsandboxer.examples.boot.domain.PurchaseOrder;
import io.misir.dbsandboxer.examples.boot.repository.ProductRepository;
import io.misir.dbsandboxer.examples.boot.repository.PurchaseOrderRepository;
import io.misir.dbsandboxer.starter.EnableDbSandboxer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = Application.class)
@EnableDbSandboxer(templateDatabaseName = "example-sqlite-template", sqliteTemplateFile = "example-sqlite-template.db")
@TestPropertySource(locations = "classpath:application-sqlite.yaml")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("sqlite")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExampleSqliteIntegrationTest {

    private static Path sandboxRoot;
    private static Path databaseFile;
    private static Path templateFile;
    private static boolean templateInitialized;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        if (sandboxRoot == null) {
            try {
                sandboxRoot = Files.createTempDirectory("dbsandboxer-example-sqlite-");
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create SQLite sandbox directory", e);
            }
            databaseFile = sandboxRoot.resolve("example-sqlite.db");
            templateFile = sandboxRoot.resolve("example-sqlite-template.db");
        }
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseFile.toAbsolutePath().toString());
    }

    @Autowired private ProductRepository products;

    @Autowired private PurchaseOrderRepository orders;

    @BeforeEach
    void refreshTemplateAfterLiquibase() throws Exception {
        if (!templateInitialized) {
            assertThat(orders.count()).isEqualTo(2);
            ensureTemplateCapturesSeedData();
            templateInitialized = true;
        }
    }

    @Test
    void sqliteTemplateIsCreatedWithBaselineData() throws Exception {
        assertThat(databaseFile).isNotEqualTo(templateFile);
        assertThat(Files.exists(templateFile)).isTrue();
        assertThat(querySkus(templateFile))
                .containsExactlyInAnyOrder("GADGET-002", "TOOL-003", "WIDGET-001");
        assertThat(queryOrderCount(templateFile)).isEqualTo(2);

        assertThat(products.count()).isEqualTo(3);
        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    void sqliteSandboxResetsBetweenTests() throws Exception {
        assertThat(products.count()).isEqualTo(3);
        assertThat(orders.count()).isEqualTo(2);

        Product saved = products.save(new Product("SQLITE-NEW", "SQLite Product", 777));
        assertThat(products.count()).isEqualTo(4);

        orders.save(new PurchaseOrder(saved, 1, 777));
        assertThat(orders.count()).isEqualTo(3);

        assertThat(querySkus(templateFile))
                .containsExactlyInAnyOrder("GADGET-002", "TOOL-003", "WIDGET-001");
        assertThat(queryOrderCount(templateFile)).isEqualTo(2);
    }

    @Test
    void sqliteSandboxRebuildsBeforeEachTest() {
        assertThat(products.count()).isEqualTo(3);
        assertThat(orders.count()).isEqualTo(2);
    }

    @AfterAll
    static void cleanupSandboxDirectory() throws IOException {
        if (sandboxRoot == null || !Files.exists(sandboxRoot)) {
            return;
        }
        try (var paths = Files.walk(sandboxRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        }
    }

    private static List<String> querySkus(Path sqliteFile) throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
                var statement = connection.prepareStatement("select sku from product order by sku");
                var rs = statement.executeQuery()) {
            List<String> skus = new ArrayList<>();
            while (rs.next()) {
                skus.add(rs.getString(1));
            }
            return skus;
        }
    }

    private static long queryOrderCount(Path sqliteFile) throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath());
                var statement = connection.prepareStatement("select count(*) from purchase_order");
                var rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private void ensureTemplateCapturesSeedData() throws Exception {
        if (databaseFile == null || templateFile == null) {
            throw new IllegalStateException("SQLite sandbox paths were not initialized");
        }

        checkpointWal(databaseFile);
        copyDatabaseArtifacts(databaseFile, templateFile);
    }

    private static void checkpointWal(Path sqliteDb) throws SQLException {
        try (Connection connection =
                        DriverManager.getConnection("jdbc:sqlite:" + sqliteDb.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private static void copyDatabaseArtifacts(Path source, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.copy(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        copyCompanion(source, target, "-wal");
        copyCompanion(source, target, "-shm");
        copyCompanion(source, target, "-journal");
    }

    private static void copyCompanion(Path source, Path target, String suffix) throws IOException {
        Path companionSource = companionFile(source, suffix);
        if (companionSource != null && Files.exists(companionSource)) {
            Files.copy(
                    companionSource,
                    companionFile(target, suffix),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static Path companionFile(Path base, String suffix) {
        if (base == null) {
            return null;
        }
        return Path.of(base.toString() + suffix);
    }
}
