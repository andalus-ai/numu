package tech.kayys.syirkah.security.domain.authentication;

public final class AuthenticationException extends RuntimeException {
    private final String code;

    public AuthenticationException(String code) {
        super(code);
        this.code = code;
    }

    public AuthenticationException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
