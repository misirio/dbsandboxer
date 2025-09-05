package io.misir.dbsandboxer.core.api;

/**
 * Exception thrown when sandbox operations fail.
 *
 * <p>This is a runtime exception that wraps any errors that occur during sandbox preparation,
 * rebuilding, or other database operations.
 *
 * @author Fethullah Misir
 */
public class SandboxException extends RuntimeException {

    /**
     * Constructs a new sandbox exception with the specified detail message.
     *
     * @param message the detail message
     */
    public SandboxException(String message) {
        super(message);
    }

    /**
     * Constructs a new sandbox exception with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public SandboxException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new sandbox exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
