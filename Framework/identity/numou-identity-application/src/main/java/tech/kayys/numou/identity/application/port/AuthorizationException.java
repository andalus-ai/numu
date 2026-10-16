package tech.kayys.syirkah.identity.application.port;

public final class AuthorizationException extends RuntimeException {
    public AuthorizationException(String permission) {
        super("Access denied: " + permission);
    }
}
