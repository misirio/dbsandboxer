package io.misir.dbsandboxer.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.misir.dbsandboxer.core.api.SandboxDatabaseProvider;
import io.misir.dbsandboxer.core.providers.sqlite.SqliteSandboxDatabaseProvider;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class DbSandboxSpringExtensionSqliteTest {

    @TempDir Path tempDir;

    @EnableDbSandboxer
    static class DefaultSqliteConfigTest {}

    @EnableDbSandboxer(sqliteTemplateFile = "templates/prebuilt.db")
    static class ExplicitTemplateFileTest {}

    @Test
    void createsSqliteProviderUsingTemplateDatabaseNameWhenNoFileSpecified() throws Exception {
        Path dbFile = tempDir.resolve("app.db");
        Path expectedTemplate = dbFile.getParent().resolve("template_database.db").normalize();

        List<List<?>> constructorArgs = new ArrayList<>();

        DbSandboxSpringExtension extension = new DbSandboxSpringExtension();
        ExtensionContext context = mockContext(DefaultSqliteConfigTest.class);
        ApplicationContext applicationContext = mockApplicationContext(dbFile);

        try (MockedStatic<SpringExtension> spring = mockStatic(SpringExtension.class);
                MockedConstruction<SqliteSandboxDatabaseProvider> sqliteConstruction =
                        mockConstruction(
                                SqliteSandboxDatabaseProvider.class,
                                (mock, constructionContext) -> {
                                    constructorArgs.add(constructionContext.arguments());
                                    doNothing().when(mock).prepareSandbox();
                                    doNothing().when(mock).rebuildSandbox();
                                })) {
            spring.when(() -> SpringExtension.getApplicationContext(context))
                    .thenReturn(applicationContext);

            extension.beforeAll(context);
            assertEquals(1, sqliteConstruction.constructed().size());
            SqliteSandboxDatabaseProvider provider = sqliteConstruction.constructed().get(0);
            verify(provider).prepareSandbox();

            extension.beforeEach(context);
            verify(provider).rebuildSandbox();
        }

        assertEquals(1, constructorArgs.size());
        List<?> args = constructorArgs.get(0);
        assertEquals(dbFile.toAbsolutePath().normalize(), args.get(0));
        assertEquals(expectedTemplate, args.get(1));
    }

    @Test
    void honoursExplicitSqliteTemplateFileRelativeToDatabase() throws Exception {
        Path dbFile = tempDir.resolve("data/mydb.sqlite");
        Path expectedTemplate = dbFile.getParent().resolve("templates/prebuilt.db").normalize();

        List<List<?>> constructorArgs = new ArrayList<>();

        DbSandboxSpringExtension extension = new DbSandboxSpringExtension();
        ExtensionContext context = mockContext(ExplicitTemplateFileTest.class);
        ApplicationContext applicationContext = mockApplicationContext(dbFile);

        try (MockedStatic<SpringExtension> spring = mockStatic(SpringExtension.class);
                MockedConstruction<SqliteSandboxDatabaseProvider> sqliteConstruction =
                        mockConstruction(
                                SqliteSandboxDatabaseProvider.class,
                                (mock, constructionContext) -> {
                                    constructorArgs.add(constructionContext.arguments());
                                    doNothing().when(mock).prepareSandbox();
                                    doNothing().when(mock).rebuildSandbox();
                                })) {
            spring.when(() -> SpringExtension.getApplicationContext(context))
                    .thenReturn(applicationContext);

            extension.beforeAll(context);
            assertEquals(1, sqliteConstruction.constructed().size());
            SqliteSandboxDatabaseProvider provider = sqliteConstruction.constructed().get(0);
            verify(provider).prepareSandbox();

            extension.beforeEach(context);
            verify(provider).rebuildSandbox();
        }

        assertEquals(1, constructorArgs.size());
        List<?> args = constructorArgs.get(0);
        assertEquals(dbFile.toAbsolutePath().normalize(), args.get(0));
        assertEquals(expectedTemplate, args.get(1));
        assertTrue(expectedTemplate.startsWith(dbFile.getParent()));
    }

    private static ExtensionContext mockContext(Class<?> testClass) {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getTestClass()).thenReturn(Optional.of(testClass));
        when(context.getParent()).thenReturn(Optional.empty());
        return context;
    }

    private static ApplicationContext mockApplicationContext(Path dbFile) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:sqlite:" + dbFile.toString());

        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(DataSource.class)).thenReturn(dataSource);
        when(context.getBean(SandboxDatabaseProvider.class))
                .thenThrow(new NoSuchBeanDefinitionException(SandboxDatabaseProvider.class));
        return context;
    }
}
