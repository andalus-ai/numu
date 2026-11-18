package tech.kayys.numou.security.vault.postgres;

/** Compare-and-append failed because another writer advanced the secret version. */
public final class SecretVersionConflictException extends RuntimeException {
    public SecretVersionConflictException(int expected, int actual) {
        super("Secret version conflict: expected previous version " + expected + " but found " + actual);
    }
    public SecretVersionConflictException(int expected, int actual, Throwable cause) {
        super("Secret version conflict: expected previous version " + expected + " but found " + actual, cause);
    }
}
