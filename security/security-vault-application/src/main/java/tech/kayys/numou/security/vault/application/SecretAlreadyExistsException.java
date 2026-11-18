package tech.kayys.numou.security.vault.application;

/** Raised when a secret identifier already has a lifecycle record or stored version. */
public final class SecretAlreadyExistsException extends RuntimeException {
    public SecretAlreadyExistsException() {
        super("Secret already exists");
    }
}
