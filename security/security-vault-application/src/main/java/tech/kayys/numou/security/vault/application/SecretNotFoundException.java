package tech.kayys.numou.security.vault.application;

public final class SecretNotFoundException extends RuntimeException {
    public SecretNotFoundException() { super("Secret was not found"); }
}
