/**
 * PostgreSQL implementation of the sandbox provider.
 *
 * <p>This package contains the PostgreSQL-specific implementation of the database sandboxing
 * functionality. The implementation uses PostgreSQL's template database feature to create fast,
 * isolated database copies for testing purposes.
 *
 * <p>The main class is {@link
 * io.misir.dbsandboxer.core.providers.postgres.PostgresSandboxDatabaseProvider} which creates a
 * template database once and then uses it to rapidly provision test database copies.
 *
 */
package io.misir.dbsandboxer.core.providers.postgres;
