package io.misir.dbsandboxer.core.providers.sqlite;

import io.misir.dbsandboxer.core.api.SandboxDatabaseProvider;
import io.misir.dbsandboxer.core.api.SandboxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite implementation of {@link SandboxDatabaseProvider}.
 *
 * <p>This provider manages SQLite database files by maintaining a template file that is copied
 * before each test execution. The template is created once from the primary database file and
 * reused to provide deterministic database state for every test.
 */
public final class SqliteSandboxDatabaseProvider implements SandboxDatabaseProvider {

    private static final Logger log =
            LoggerFactory.getLogger(SqliteSandboxDatabaseProvider.class);

    private static final AtomicBoolean TEMPLATE_READY = new AtomicBoolean(false);

    private final Path databaseFile;
    private final Path templateFile;

    /**
     * Creates a new SQLite sandbox database provider.
     *
     * @param databaseFile the primary database file that tests will connect to
     * @param templateFile the template database file that will be copied before each test
     */
    public SqliteSandboxDatabaseProvider(Path databaseFile, Path templateFile) {
        Path normalizedDatabase =
                Objects.requireNonNull(databaseFile, "databaseFile cannot be null")
                        .toAbsolutePath()
                        .normalize();
        Path normalizedTemplate =
                Objects.requireNonNull(templateFile, "templateFile cannot be null")
                        .toAbsolutePath()
                        .normalize();
        if (normalizedDatabase.equals(normalizedTemplate)) {
            throw new IllegalArgumentException(
                    "databaseFile and templateFile must be different files");
        }
        this.databaseFile = normalizedDatabase;
        this.templateFile = normalizedTemplate;
    }

    /**
     * Creates a new SQLite sandbox database provider.
     *
     * @param databaseFile the primary database file path
     * @param templateFile the template database file path
     */
    public SqliteSandboxDatabaseProvider(String databaseFile, String templateFile) {
        this(Path.of(databaseFile), Path.of(templateFile));
    }

    @Override
    public void prepareSandbox() {
        if (TEMPLATE_READY.get() && Files.exists(templateFile)) {
            return;
        }
        synchronized (TEMPLATE_READY) {
            if (TEMPLATE_READY.get() && Files.exists(templateFile)) {
                return;
            }
            if (!Files.exists(templateFile)) {
                createTemplate();
            }
            TEMPLATE_READY.set(true);
        }
    }

    @Override
    public void rebuildSandbox() {
        if (!Files.exists(templateFile)) {
            throw new SandboxException(
                    "SQLite template database does not exist. Call prepareSandbox() first.");
        }
        try {
            ensureParentDirectory(databaseFile);
            deleteDatabaseArtifacts(databaseFile);
            copyDatabaseArtifacts(templateFile, databaseFile);
        } catch (IOException e) {
            throw new SandboxException("Failed to rebuild SQLite sandbox database", e);
        }
    }

    @Override
    public void cleanupSandbox() {
        synchronized (TEMPLATE_READY) {
            TEMPLATE_READY.set(false);
        }
        try {
            deleteDatabaseArtifacts(templateFile);
        } catch (IOException e) {
            throw new SandboxException("Failed to clean up SQLite sandbox database", e);
        }
    }

    private void createTemplate() {
        if (!Files.exists(databaseFile)) {
            throw new SandboxException(
                    "Primary SQLite database does not exist: " + databaseFile.toAbsolutePath());
        }
        log.info("Building SQLite template database “{}”…", templateFile);
        try {
            ensureParentDirectory(templateFile);
            deleteDatabaseArtifacts(templateFile);
            copyDatabaseArtifacts(databaseFile, templateFile);
        } catch (IOException e) {
            throw new SandboxException("Failed to create SQLite template database", e);
        }
        log.info("SQLite template ready at “{}”.", templateFile);
    }

    private static void ensureParentDirectory(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void deleteDatabaseArtifacts(Path base) throws IOException {
        deleteIfExists(base);
        deleteIfExists(companionFile(base, "-wal"));
        deleteIfExists(companionFile(base, "-shm"));
        deleteIfExists(companionFile(base, "-journal"));
    }

    private static void deleteIfExists(Path file) throws IOException {
        if (file != null && Files.exists(file)) {
            Files.delete(file);
        }
    }

    private static void copyDatabaseArtifacts(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("SQLite source database not found: " + source);
        }
        ensureParentDirectory(target);
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
            Path companionTarget = companionFile(target, suffix);
            ensureParentDirectory(companionTarget);
            Files.copy(
                    companionSource,
                    companionTarget,
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
