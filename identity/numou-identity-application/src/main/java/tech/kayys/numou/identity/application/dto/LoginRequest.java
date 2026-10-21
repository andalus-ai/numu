package tech.kayys.numou.identity.application.dto;

/**
 * Local credential login payload.
 *
 * @param rememberMe when true the server also mints a long-lived credential
 *                   (see {@link tech.kayys.numou.identity.application.service.TokenService})
 *                   that the UI stores in a cookie and sends as an alternate bearer token.
 */
public record LoginRequest(
        String username,
        String password,
        boolean rememberMe
) {}
