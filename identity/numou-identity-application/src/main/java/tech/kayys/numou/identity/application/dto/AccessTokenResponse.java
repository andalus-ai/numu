package tech.kayys.numou.identity.application.dto;

/**
 * Response to a successful local login.
 *
 * @param accessToken   short-lived JWT (default 15 minutes)
 * @param rememberMeToken long-lived credential minted when {@link LoginRequest#rememberMe}
 *                      was set, meant to be stored in a cookie and sent as an alternate
 *                      bearer token for the same user.
 */
public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String rememberMeToken
) {
    public static AccessTokenResponse bearer(String token, long expiresIn) {
        return new AccessTokenResponse(token, "Bearer", expiresIn, null);
    }
}
