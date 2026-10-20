package tech.kayys.numou.identity.application.dto;

public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AccessTokenResponse bearer(String token, long expiresIn) {
        return new AccessTokenResponse(token, "Bearer", expiresIn);
    }
}
