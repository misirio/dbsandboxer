package io.misir.dbsandboxer.examples.boot;

import io.misir.dbsandboxer.starter.EnableDbSandboxer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {ProjectTestConfiguration.class})
@EnableDbSandboxer
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
public class BaseIntegrationTest {}
