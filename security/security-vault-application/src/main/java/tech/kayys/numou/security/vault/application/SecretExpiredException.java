package tech.kayys.numou.security.vault.application;

public final class SecretExpiredException extends RuntimeException {
    public SecretExpiredException() { super("Secret version has expired"); }
}
