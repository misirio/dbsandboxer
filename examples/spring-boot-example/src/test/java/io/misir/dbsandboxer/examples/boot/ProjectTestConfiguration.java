package io.misir.dbsandboxer.examples.boot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ProjectTestConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("admin")
                .withUsername("postgres")
                .withPassword("postgres");
    }
}
