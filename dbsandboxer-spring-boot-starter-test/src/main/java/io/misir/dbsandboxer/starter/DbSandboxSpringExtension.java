package io.misir.dbsandboxer.starter;

import io.misir.dbsandboxer.core.api.SandboxDatabaseProvider;
import io.misir.dbsandboxer.core.api.SandboxException;
import io.misir.dbsandboxer.core.providers.postgres.PostgresSandboxDatabaseProvider;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public final class DbSandboxSpringExtension implements BeforeAllCallback, BeforeEachCallback {

    private SandboxDatabaseProvider provider;

    @Override
    public void beforeAll(ExtensionContext ctx) throws Exception {
        ApplicationContext appCtx = SpringExtension.getApplicationContext(ctx);
        DataSource ds = appCtx.getBean(DataSource.class);
        // Prefer an existing DatabaseProvider bean if available
        SandboxDatabaseProvider p;
        try {
            p = appCtx.getBean(SandboxDatabaseProvider.class);
        } catch (Exception noBean) {
            // Fallback: derive a PostgresProvider from DataSource URL + annotation config
            EnableDbSandboxer cfg =
                    ctx.getRequiredTestClass().getAnnotation(EnableDbSandboxer.class);
            DbUrlParts url = inspectUrl(ds);
            p =
                    new PostgresSandboxDatabaseProvider(
                            url.host,
                            url.port,
                            cfg.maintenanceDb(),
                            cfg.adminUser(),
                            cfg.adminPassword(),
                            url.primaryDatabaseName(),
                            cfg.templateDatabaseName());
        }
        this.provider = p;
        this.provider.prepareSandbox();
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        if (provider == null) {
            throw new SandboxException("No PostgreSQL database provider available");
        }
        provider.rebuildSandbox();
    }

    private static DbUrlParts inspectUrl(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection()) {
            String url = c.getMetaData().getURL();
            // expected: jdbc:postgresql://host:port/dbname[?params]
            if (url == null) throw new SQLException("DataSource URL is null");
            String noPrefix = url;
            int idx = noPrefix.indexOf("://");
            if (idx >= 0) noPrefix = noPrefix.substring(idx + 3);
            // now noPrefix = host:port/dbname?...
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
            return new DbUrlParts(host, port, primaryDatabaseName);
        }
    }

    private record DbUrlParts(String host, int port, String primaryDatabaseName) {}
}
