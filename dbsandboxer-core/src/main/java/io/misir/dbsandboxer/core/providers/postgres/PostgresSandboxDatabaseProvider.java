package io.misir.dbsandboxer.core.providers.postgres;

import io.misir.dbsandboxer.core.api.SandboxDatabaseProvider;
import io.misir.dbsandboxer.core.api.SandboxException;
import java.sql.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL implementation of the SandboxDatabaseProvider.
 *
 * <p>This provider uses PostgreSQL's template database feature to create fast, isolated database
 * copies for testing. It creates a template database once with the full schema and test data, then
 * uses PostgreSQL's CREATE DATABASE with TEMPLATE option to rapidly create test database copies.
 *
 * <p>This approach is significantly faster than replaying migrations for each test, typically
 * taking only milliseconds to create a new database copy.
 *
 * @author Fethullah Misir
 */
public final class PostgresSandboxDatabaseProvider implements SandboxDatabaseProvider {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresSandboxDatabaseProvider.class);

    private static final AtomicBoolean TEMPLATE_READY = new AtomicBoolean(false);
    private static final Pattern SAFE_DB_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final String host;
    private final int port;
    private final String adminDatabase;
    private final String adminUser;
    private final String adminPassword;

    private final String primaryDatabaseName;
    private final String templateName;

    /**
     * Creates a new PostgreSQL sandbox database provider.
     *
     * @param host the database host
     * @param port the database port (1-65535)
     * @param adminDatabaseName the admin/maintenance database name (usually "postgres")
     * @param adminUser the admin user with CREATE DATABASE privileges
     * @param adminPassword the admin user's password
     * @param primaryDatabaseName the name of the primary database to sandbox
     * @param templateDatabaseName the name of the template database to create
     * @throws IllegalArgumentException if port is out of range or database names are invalid
     * @throws NullPointerException if any required parameter is null
     */
    public PostgresSandboxDatabaseProvider(
            String host,
            int port,
            String adminDatabaseName,
            String adminUser,
            String adminPassword,
            String primaryDatabaseName,
            String templateDatabaseName) {
        this.host = Objects.requireNonNull(host, "host cannot be null");

        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException(
                    "Port must be between " + MIN_PORT + " and " + MAX_PORT + ", got: " + port);
        }
        this.port = port;

        this.adminDatabase = validateDatabaseName(adminDatabaseName, "adminDatabaseName");
        this.adminUser = Objects.requireNonNull(adminUser, "adminUser cannot be null");
        this.adminPassword = Objects.requireNonNull(adminPassword, "adminPassword cannot be null");
        this.primaryDatabaseName = validateDatabaseName(primaryDatabaseName, "primaryDatabaseName");
        this.templateName = validateDatabaseName(templateDatabaseName, "templateDatabaseName");
    }

    private static String validateDatabaseName(String name, String paramName) {
        Objects.requireNonNull(name, paramName + " cannot be null");
        if (!SAFE_DB_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    paramName
                            + " contains invalid characters. "
                            + "Only alphanumeric characters and underscores are allowed: "
                            + name);
        }
        return name;
    }

    @Override
    public void prepareSandbox() {
        if (TEMPLATE_READY.get()) {
            return;
        }
        synchronized (TEMPLATE_READY) {
            if (TEMPLATE_READY.get()) {
                return;
            }
            if (!templateExists()) {
                createTemplate();
            }
            TEMPLATE_READY.set(true);
        }
    }

    /**
     * Drops the application database and recreates it from the template.
     *
     * <p>Very fast (milliseconds) because PostgreSQL copies the physical files instead of replaying
     * migrations.
     */
    @Override
    public void rebuildSandbox() {
        try (Connection admin = DriverManager.getConnection(adminUrl(), adminUser, adminPassword);
                Statement s = admin.createStatement()) {

            terminateConnections(s, primaryDatabaseName);
            s.execute("DROP DATABASE IF EXISTS " + primaryDatabaseName + ';');
            s.execute("CREATE DATABASE " + primaryDatabaseName + " TEMPLATE " + templateName + ';');

        } catch (SQLException e) {
            throw new SandboxException(e);
        }
    }

    private boolean templateExists() {
        final String sql = "SELECT 1 FROM pg_database WHERE datname = ? AND datistemplate";
        try (Connection admin = DriverManager.getConnection(adminUrl(), adminUser, adminPassword);
                PreparedStatement ps = admin.prepareStatement(sql)) {

            ps.setString(1, templateName);
            return ps.executeQuery().next();

        } catch (SQLException e) {
            throw new SandboxException(e);
        }
    }

    private void createTemplate() {
        log.info("Building template database “{}”…", templateName);
        try (Connection admin = DriverManager.getConnection(adminUrl(), adminUser, adminPassword);
                Statement s = admin.createStatement()) {

            terminateConnections(s, primaryDatabaseName);

            s.execute("ALTER DATABASE " + primaryDatabaseName + " IS_TEMPLATE true;");
            s.execute("CREATE DATABASE " + templateName + " TEMPLATE " + primaryDatabaseName + ';');
            s.execute("ALTER DATABASE " + primaryDatabaseName + " IS_TEMPLATE false;");
            s.execute("ALTER DATABASE " + templateName + " IS_TEMPLATE true;");

        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        log.info("Template “{}” ready.", templateName);
    }

    /** JDBC URL for the admin database. */
    private String adminUrl() {
        return "jdbc:postgresql://" + host + ':' + port + '/' + adminDatabase;
    }

    /**
     * Terminates all connections to {@code db} except our own, so we can drop/alter the database
     * safely.
     */
    private static void terminateConnections(Statement s, String db) throws SQLException {
        s.execute(
                """
                  SELECT pg_terminate_backend(pid)
                  FROM   pg_stat_activity
                  WHERE  datname = '%s' AND pid <> pg_backend_pid()
                """
                        .formatted(db));
    }
}
