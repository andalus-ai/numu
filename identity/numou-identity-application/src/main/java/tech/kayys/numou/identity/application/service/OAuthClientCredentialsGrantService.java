package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.dto.OAuthAccessTokenResponse;
import tech.kayys.numou.identity.application.port.OAuthAccessTokenIssuerPort;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.application.security.ClientSecretGenerator;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** OAuth 2.0 client_credentials grant for registered developer applications. */
public final class OAuthClientCredentialsGrantService {
    private final OAuthClientCredentialRepository credentials;
    private final ClientSecretGenerator secretGenerator;
    private final OAuthAccessTokenIssuerPort tokenIssuer;
    private final Clock clock;
    private final long expiresInSeconds;

    public OAuthClientCredentialsGrantService(OAuthClientCredentialRepository credentials,
            ClientSecretGenerator secretGenerator, OAuthAccessTokenIssuerPort tokenIssuer,
            Clock clock, long expiresInSeconds) {
        this.credentials = credentials; this.secretGenerator = secretGenerator;
        this.tokenIssuer = tokenIssuer; this.clock = clock;
        if (expiresInSeconds < 30 || expiresInSeconds > 900)
            throw new IllegalArgumentException("OAuth client token lifetime must be between 30 and 900 seconds");
        this.expiresInSeconds = expiresInSeconds;
    }

    public Uni<OAuthAccessTokenResponse> exchange(String clientId, String clientSecret, String scopeParameter) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank())
            return Uni.createFrom().failure(OAuthGrantException.invalidClient());
        final Set<String> requestedScopes;
        try { requestedScopes = parseScopes(scopeParameter); }
        catch (IllegalArgumentException invalidScope) { return Uni.createFrom().failure(OAuthGrantException.invalidScope()); }

        return credentials.findByClientId(clientId).onItem().transformToUni(found -> {
            if (found.isEmpty()) return Uni.createFrom().failure(OAuthGrantException.invalidClient());
            OAuthClientCredential credential = found.orElseThrow();
            Instant now = clock.instant();
            if (!credential.isUsableAt(now) || !secretGenerator.matches(clientSecret, credential.secretHash()))
                return Uni.createFrom().failure(OAuthGrantException.invalidClient());
            if (!credential.scopes().containsAll(requestedScopes))
                return Uni.createFrom().failure(OAuthGrantException.invalidScope());
            String token = tokenIssuer.issueClientAccessToken(credential.clientId(), credential.tenantId(),
                    credential.applicationId(), requestedScopes, credential.credentialVersion());
            String scope = requestedScopes.stream().sorted().collect(Collectors.joining(" "));
            return Uni.createFrom().item(new OAuthAccessTokenResponse(token, "Bearer", expiresInSeconds, scope));
        });
    }

    private static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank() || value.length() > 2048)
            throw new IllegalArgumentException("scope is required and must be bounded");
        Set<String> scopes = Arrays.stream(value.trim().split("\\s+"))
                .filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
        if (scopes.isEmpty() || scopes.size() > 64) throw new IllegalArgumentException("invalid scope count");
        for (String scope : scopes)
            if (!scope.matches("[a-z][a-z0-9]*(?:[.:][a-z0-9][a-z0-9-]*)*"))
                throw new IllegalArgumentException("invalid scope syntax");
        return scopes;
    }
}
