package io.misir.dbsandboxer.starter;

import io.misir.dbsandboxer.core.api.SandboxDatabaseProvider;
import io.misir.dbsandboxer.core.api.SandboxException;
import io.misir.dbsandboxer.core.providers.postgres.PostgresSandboxDatabaseProvider;
import io.misir.dbsandboxer.core.providers.sqlite.SqliteSandboxDatabaseProvider;
import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public final class DbSandboxSpringExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private SandboxDatabaseProvider provider;
    private EnableDbSandboxer configuration;

    @Override
    public void beforeAll(ExtensionContext ctx) throws Exception {
        ApplicationContext appCtx = SpringExtension.getApplicationContext(ctx);
        DataSource ds = appCtx.getBean(DataSource.class);
        EnableDbSandboxer cfg = resolveConfiguration(ctx);
        this.configuration = cfg;
        // Prefer an existing DatabaseProvider bean if available
        SandboxDatabaseProvider p;
        try {
            p = appCtx.getBean(SandboxDatabaseProvider.class);
        } catch (Exception noBean) {
            // Fallback: derive provider from DataSource URL + annotation config
            DbUrlParts url = inspectUrl(ds);
            switch (url.type()) {
                case POSTGRESQL ->
                        p =
                                new PostgresSandboxDatabaseProvider(
                                        url.host(),
                                        url.port(),
                                        cfg.maintenanceDb(),
                                        cfg.adminUser(),
                                        cfg.adminPassword(),
                                        url.primaryDatabaseName(),
                                        cfg.templateDatabaseName());
                case SQLITE -> {
                    Path template =
                            resolveTemplatePath(
                                    url.sqlitePath(), cfg.templateDatabaseName());
                    p = new SqliteSandboxDatabaseProvider(url.sqlitePath(), template);
                }
                default ->
                        throw new SandboxException(
                                "Unsupported database type for DbSandboxer: " + url.type());
            }
        }
        this.provider = p;
        this.provider.prepareSandbox();
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        if (provider == null) {
            throw new SandboxException("No SandboxDatabaseProvider available");
        }
        provider.rebuildSandbox();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (provider != null
                && configuration != null
                && configuration.dropTemplateDatabase()) {
            provider.cleanupSandbox();
        }
    }

    private static EnableDbSandboxer resolveConfiguration(ExtensionContext context) {
        EnableDbSandboxer cfg =
                context.getTestClass().map(DbSandboxSpringExtension::findAnnotation).orElse(null);
        if (cfg != null) {
            return cfg;
        }

        ExtensionContext current = context.getParent().orElse(null);
        while (current != null) {
            cfg = current.getTestClass().map(DbSandboxSpringExtension::findAnnotation).orElse(null);
            if (cfg != null) {
                return cfg;
            }
            current = current.getParent().orElse(null);
        }

        throw new SandboxException(
                "@EnableDbSandboxer annotation not found. Please annotate your test class.");
    }

    private static EnableDbSandboxer findAnnotation(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            EnableDbSandboxer annotation = current.getAnnotation(EnableDbSandboxer.class);
            if (annotation != null) {
                return annotation;
            }
            current = current.getEnclosingClass();
        }
        return null;
    }

    private static DbUrlParts inspectUrl(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            String url = c.getMetaData().getURL();
            if (url == null) throw new SQLException("DataSource URL is null");
            if (url.startsWith("jdbc:postgresql:")) {
                return parsePostgresUrl(url);
            }
            if (url.startsWith("jdbc:sqlite:")) {
                Path sqlitePath = parseSqlitePath(url);
                return new DbUrlParts(DatabaseType.SQLITE, null, 0, null, sqlitePath);
            }
            throw new SandboxException("Unsupported JDBC URL: " + url);
        }
    }

    private static DbUrlParts parsePostgresUrl(String url) {
        String noPrefix = url;
        int idx = noPrefix.indexOf("://");
        if (idx >= 0) noPrefix = noPrefix.substring(idx + 3);
        String hostPortDb = noPrefix;
        int slash = hostPortDb.indexOf('/');
        String hostPort = slash > 0 ? hostPortDb.substring(0, slash) : hostPortDb;
        String primaryDatabaseName = slash > 0 ? hostPortDb.substring(slash + 1) : "";
        int q = primaryDatabaseName.indexOf('?');
        if (q >= 0) primaryDatabaseName = primaryDatabaseName.substring(0, q);
        String host = hostPort;
        int colon = hostPort.indexOf(':');
        int port = 5432;
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ignore) {
            }
        }
        return new DbUrlParts(DatabaseType.POSTGRESQL, host, port, primaryDatabaseName, null);
    }

    private static Path parseSqlitePath(String url) {
        String pathPart = url.substring("jdbc:sqlite:".length());
        int queryIndex = pathPart.indexOf('?');
        if (queryIndex >= 0) {
            pathPart = pathPart.substring(0, queryIndex);
        }
        if (pathPart.isBlank() || ":memory:".equals(pathPart)) {
            throw new SandboxException("SQLite in-memory databases are not supported for sandboxing");
        }
        if (pathPart.startsWith("file:")) {
            try {
                URI uri = URI.create(pathPart);
                return Path.of(uri).toAbsolutePath().normalize();
            } catch (RuntimeException e) {
                throw new SandboxException("Invalid SQLite file URI: " + pathPart, e);
            }
        }
        try {
            return Path.of(pathPart).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new SandboxException("Invalid SQLite database path: " + pathPart, e);
        }
    }

    private static Path resolveTemplatePath(Path databaseFile, String templateName) {
        String candidate = templateName;
        if (candidate == null || candidate.isBlank()) {
            candidate = "template_database";
        }
        try {
            Path template = Path.of(candidate);
            Path fileName = template.getFileName();
            if (fileName != null && !fileName.toString().contains(".")) {
                template = template.resolveSibling(fileName + ".db");
            }
            Path normalizedDb = databaseFile.toAbsolutePath().normalize();
            if (!template.isAbsolute()) {
                template = normalizedDb.resolveSibling(template).normalize();
            } else {
                template = template.normalize();
            }
            return template;
        } catch (InvalidPathException e) {
            throw new SandboxException("Invalid SQLite template path: " + candidate, e);
        }
    }

    private enum DatabaseType {
        POSTGRESQL,
        SQLITE
    }

    private record DbUrlParts(
            DatabaseType type, String host, int port, String primaryDatabaseName, Path sqlitePath) {}
}
