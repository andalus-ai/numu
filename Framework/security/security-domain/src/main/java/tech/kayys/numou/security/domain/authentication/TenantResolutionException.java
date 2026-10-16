package tech.kayys.numou.security.domain.authentication;

public final class TenantResolutionException extends RuntimeException {
    private final String code;

    public TenantResolutionException(String code) {
        super(code);
        this.code = code;
    }

    public TenantResolutionException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
