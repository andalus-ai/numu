package tech.kayys.numou.identity.application.service;
/** Safe OAuth token endpoint error; messages never contain supplied credentials. */
public final class OAuthGrantException extends RuntimeException {
    private final String error;
    private final int httpStatus;
    public OAuthGrantException(String error, int httpStatus) { super(error); this.error = error; this.httpStatus = httpStatus; }
    public String error() { return error; }
    public int httpStatus() { return httpStatus; }
    public static OAuthGrantException invalidClient() { return new OAuthGrantException("invalid_client", 401); }
    public static OAuthGrantException invalidScope() { return new OAuthGrantException("invalid_scope", 400); }
}
