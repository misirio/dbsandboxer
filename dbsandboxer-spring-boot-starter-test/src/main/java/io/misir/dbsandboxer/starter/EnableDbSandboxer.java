package io.misir.dbsandboxer.starter;

import java.lang.annotation.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enables database sandboxing for Spring Boot integration tests.
 *
 * <p>Apply this annotation to your test class to activate database sandboxing. Each test method
 * will run with a fresh copy of the database, ensuring complete isolation between tests.
 *
 * <pre>{@code
 * @SpringBootTest
 * @EnableDbSandboxer
 * public class MyIntegrationTest {
 *     // Each test gets a fresh database copy
 * }
 * }</pre>
 *
 * @author Fethullah Misir
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(DbSandboxSpringExtension.class)
public @interface EnableDbSandboxer {

    /**
     * The admin user for database operations (DROP/CREATE).
     *
     * <p>This user must have sufficient privileges to create and drop databases.
     *
     * @return the admin username, defaults to "postgres"
     */
    String adminUser() default "postgres";

    /**
     * The admin user's password.
     *
     * <p>If not specified, the extension will attempt to use the DataSource's password if
     * accessible.
     *
     * @return the admin password, defaults to "postgres"
     */
    String adminPassword() default "postgres";

    /**
     * The maintenance database name.
     *
     * <p>PostgreSQL requires a connection to an existing database to perform administrative
     * operations. This is typically "postgres" but can be configured if your setup uses a different
     * maintenance database.
     *
     * @return the maintenance database name, defaults to "postgres"
     */
    String maintenanceDb() default "postgres";

    /**
     * The name of the template database.
     *
     * <p>This database will be created once with your schema and test data, then used as a template
     * for creating test database copies.
     *
     * @return the template database name, defaults to "template_database"
     */
    String templateDatabaseName() default "template_database";
}
