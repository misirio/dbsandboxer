package io.misir.dbsandboxer.examples.boot;

import io.misir.dbsandboxer.starter.EnableDbSandboxer;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {ProjectTestConfiguration.class})
@EnableDbSandboxer
public class BaseIntegrationTest {}
