package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Checks live credential state behind an already cryptographically verified OAuth access token.
 * JWT signature, issuer, audience and temporal claims must be verified before this service is called.
 */
public final class OAuthClientAccessTokenStateValidator {
    private final OAuthClientCredentialRepository credentials;
    private final Clock clock;

    public OAuthClientAccessTokenStateValidator(OAuthClientCredentialRepository credentials, Clock clock) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<VerifiedClientAccessToken> validate(String tokenUse, String clientId, String tenantId,
            String applicationId, long credentialVersion, Set<String> tokenScopes) {
        if (!"access".equals(tokenUse) || blank(clientId) || clientId.length() > 96
                || blank(tenantId) || tenantId.length() > 36 || blank(applicationId) || applicationId.length() > 36
                || credentialVersion < 1 || !validScopes(tokenScopes)) {
            return Uni.createFrom().failure(OAuthTokenRejectedException.invalidToken());
        }
        final UUID claimedTenant;
        final UUID claimedApplication;
        try {
            claimedTenant = UUID.fromString(tenantId);
            claimedApplication = UUID.fromString(applicationId);
        } catch (IllegalArgumentException malformed) {
            return Uni.createFrom().failure(OAuthTokenRejectedException.invalidToken());
        }

        return credentials.findByClientId(clientId).onItem().transform(found -> {
            OAuthClientCredential credential = found.orElseThrow(OAuthTokenRejectedException::invalidToken);
            Instant now = clock.instant();
            if (!credential.isUsableAt(now)
                    || !credential.tenantId().equals(claimedTenant)
                    || !credential.applicationId().equals(claimedApplication)
                    || credential.credentialVersion() != credentialVersion
                    || !credential.scopes().containsAll(tokenScopes)) {
                throw OAuthTokenRejectedException.invalidToken();
            }
            return new VerifiedClientAccessToken(credential.clientId(), credential.tenantId(),
                    credential.applicationId(), credential.credentialVersion(), Set.copyOf(tokenScopes));
        });
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static boolean validScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty() || scopes.size() > 64) return false;
        return scopes.stream().allMatch(scope -> scope != null && scope.length() <= 128
                && scope.matches("[a-z][a-z0-9]*(?:[.:][a-z0-9][a-z0-9-]*)*"));
    }

    public record VerifiedClientAccessToken(String clientId, UUID tenantId, UUID applicationId,
            long credentialVersion, Set<String> scopes) {
        public VerifiedClientAccessToken { scopes = Set.copyOf(scopes); }
    }
}
