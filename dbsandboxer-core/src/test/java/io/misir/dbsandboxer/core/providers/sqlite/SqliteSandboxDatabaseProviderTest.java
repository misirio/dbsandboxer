package io.misir.dbsandboxer.core.providers.sqlite;

import static org.assertj.core.api.Assertions.*;

import io.misir.dbsandboxer.core.api.SandboxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SqliteSandboxDatabaseProvider Tests")
class SqliteSandboxDatabaseProviderTest {

    @TempDir Path tempDir;

    private Path primaryDb;
    private Path templateDb;
    private SqliteSandboxDatabaseProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        primaryDb = tempDir.resolve("primary.db");
        templateDb = tempDir.resolve("template.db");
        createPrimaryDatabase();
        provider = new SqliteSandboxDatabaseProvider(primaryDb, templateDb);
    }

    @Test
    @DisplayName("prepareSandbox should create template on first call")
    void prepareSandboxShouldCreateTemplateOnFirstCall() throws Exception {
        provider.prepareSandbox();

        assertThat(Files.exists(templateDb)).isTrue();
        assertThat(countUsers(primaryDb)).isEqualTo(2);
        assertThat(countUsers(templateDb)).isEqualTo(2);
    }

    @Test
    @DisplayName("prepareSandbox should be idempotent")
    void prepareSandboxShouldBeIdempotent() {
        provider.prepareSandbox();
        long modified = fileTimestamp(templateDb);

        provider.prepareSandbox();
        provider.prepareSandbox();

        assertThat(fileTimestamp(templateDb)).isEqualTo(modified);
    }

    @Test
    @DisplayName("rebuildSandbox should restore template state")
    void rebuildSandboxShouldRestoreTemplateState() throws Exception {
        provider.prepareSandbox();
        provider.rebuildSandbox();

        insertUser(primaryDb, "Charlie");
        assertThat(countUsers(primaryDb)).isEqualTo(3);

        provider.rebuildSandbox();

        assertThat(countUsers(primaryDb)).isEqualTo(2);
        assertThat(fetchUserNames(primaryDb)).containsExactly("Alice", "Bob");
    }

    @Test
    @DisplayName("rebuildSandbox should throw when template missing")
    void rebuildSandboxShouldThrowWhenTemplateMissing() throws Exception {
        provider.prepareSandbox();
        Files.delete(templateDb);

        assertThatThrownBy(provider::rebuildSandbox)
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("template");
    }

    @Test
    @DisplayName("prepareSandbox should fail when primary database is missing")
    void prepareSandboxShouldFailWhenPrimaryDatabaseMissing() throws Exception {
        Path missing = tempDir.resolve("missing.db");
        SqliteSandboxDatabaseProvider badProvider =
                new SqliteSandboxDatabaseProvider(missing, templateDb);

        assertThatThrownBy(badProvider::prepareSandbox)
                .isInstanceOf(SandboxException.class)
                .hasMessageContaining("Primary SQLite database");
    }

    @Test
    @DisplayName("cleanupSandbox should delete template file")
    void cleanupSandboxShouldDeleteTemplateFile() throws Exception {
        provider.prepareSandbox();
        provider.rebuildSandbox();

        assertThat(Files.exists(templateDb)).isTrue();
        assertThat(Files.exists(primaryDb)).isTrue();

        provider.cleanupSandbox();

        assertThat(Files.exists(templateDb)).isFalse();
        assertThat(Files.exists(primaryDb)).isTrue();

        provider.prepareSandbox();
        assertThat(Files.exists(templateDb)).isTrue();
    }

    private void createPrimaryDatabase() throws SQLException {
        try (Connection connection = connect(primaryDb);
                Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=DELETE");
            stmt.execute(
                    "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
            stmt.execute("INSERT INTO users (name) VALUES ('Alice'), ('Bob')");
        }
    }

    private long fileTimestamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int countUsers(Path database) throws SQLException {
        try (Connection connection = connect(database);
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertUser(Path database, String name) throws SQLException {
        try (Connection connection = connect(database);
                Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO users (name) VALUES ('" + name + "')");
        }
    }

    private List<String> fetchUserNames(Path database) throws SQLException {
        try (Connection connection = connect(database);
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT name FROM users ORDER BY name")) {
            return resultSetToList(rs);
        }
    }

    private List<String> resultSetToList(ResultSet rs) throws SQLException {
        List<String> names = new java.util.ArrayList<>();
        while (rs.next()) {
            names.add(rs.getString(1));
        }
        return names;
    }

    private Connection connect(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }
}
