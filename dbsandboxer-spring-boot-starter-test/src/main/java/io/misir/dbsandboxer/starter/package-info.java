/**
 * Spring Boot integration for database sandboxing.
 *
 * <p>This package provides seamless integration with Spring Boot tests through JUnit 5 extensions.
 * The main entry point is the {@link io.misir.dbsandboxer.starter.EnableDbSandboxer} annotation
 * which activates database sandboxing for your integration tests.
 *
 * <p>The extension automatically detects your Spring Boot DataSource configuration and sets up
 * database sandboxing to ensure each test runs with a fresh database copy.
 *
 */
package io.misir.dbsandboxer.starter;
