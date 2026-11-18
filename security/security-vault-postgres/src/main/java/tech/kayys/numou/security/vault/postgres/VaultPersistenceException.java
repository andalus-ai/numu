package tech.kayys.numou.security.vault.postgres;

/** Persistence failure without exposing secret contents or SQL parameters. */
public final class VaultPersistenceException extends RuntimeException {
    public VaultPersistenceException(String message, Throwable cause) { super(message, cause); }
}
