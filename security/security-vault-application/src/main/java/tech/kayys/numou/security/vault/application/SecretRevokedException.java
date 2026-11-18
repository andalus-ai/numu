package tech.kayys.numou.security.vault.application;

public final class SecretRevokedException extends RuntimeException {
    public SecretRevokedException() { super("Secret is revoked"); }
}
