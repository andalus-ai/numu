package tech.kayys.numou.identity.application.service;

/** Generic rejection for invalid, stale, revoked, or mismatched machine access tokens. */
public final class OAuthTokenRejectedException extends RuntimeException {
    private OAuthTokenRejectedException() { super("OAuth access token is not active"); }
    public static OAuthTokenRejectedException invalidToken() { return new OAuthTokenRejectedException(); }
}
