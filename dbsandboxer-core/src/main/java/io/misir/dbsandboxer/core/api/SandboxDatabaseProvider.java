package io.misir.dbsandboxer.core.api;

/**
 * Provider interface for database sandbox operations.
 *
 * <p>Implementations of this interface manage the lifecycle of sandboxed database instances used
 * for testing. The sandbox pattern ensures complete test isolation by providing each test with a
 * fresh copy of the database.
 *
 * @author Fethullah Misir
 */
public interface SandboxDatabaseProvider {

    /**
     * Prepares the sandbox environment.
     *
     * <p>This method should be called once before tests begin to set up the database that will be
     * used as the source for creating test database copies. Implementations should make this method
     * idempotent - multiple calls should not recreate the template if it already exists.
     *
     * @throws SandboxException if the sandbox preparation fails
     */
    void prepareSandbox() throws SandboxException;

    /**
     * Rebuilds the sandbox.
     *
     * <p>This method should be called before each test to ensure a clean database state.
     *
     * @throws SandboxException if the sandbox rebuild fails
     */
    void rebuildSandbox() throws SandboxException;
}
