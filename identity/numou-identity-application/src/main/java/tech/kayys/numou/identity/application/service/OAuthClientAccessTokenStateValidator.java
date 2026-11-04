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
        if (!"access".equals(tokenUse) || blank(clientId) || blank(tenantId) || blank(applicationId)
                || credentialVersion < 1 || tokenScopes == null || tokenScopes.isEmpty()) {
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

    public record VerifiedClientAccessToken(String clientId, UUID tenantId, UUID applicationId,
            long credentialVersion, Set<String> scopes) {
        public VerifiedClientAccessToken { scopes = Set.copyOf(scopes); }
    }
}
