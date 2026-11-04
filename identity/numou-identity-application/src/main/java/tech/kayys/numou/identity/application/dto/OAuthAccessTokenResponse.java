package tech.kayys.numou.identity.application.dto;
/** OAuth 2.0 token response; never includes client-secret material. */
public record OAuthAccessTokenResponse(String access_token, String token_type, long expires_in, String scope) {
    public OAuthAccessTokenResponse {
        if (access_token == null || access_token.isBlank()) throw new IllegalArgumentException("access_token is required");
        if (!"Bearer".equalsIgnoreCase(token_type)) throw new IllegalArgumentException("token_type must be Bearer");
        if (expires_in < 1) throw new IllegalArgumentException("expires_in must be positive");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope is required");
    }
}
