package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialId;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence contract for machine client credentials. */
public interface OAuthClientCredentialRepository {
    Uni<Optional<OAuthClientCredential>> findById(UUID tenantId, OAuthClientCredentialId id);
    Uni<Optional<OAuthClientCredential>> findByClientId(UUID tenantId, String clientId);
    Uni<Void> save(OAuthClientCredential credential);
}
