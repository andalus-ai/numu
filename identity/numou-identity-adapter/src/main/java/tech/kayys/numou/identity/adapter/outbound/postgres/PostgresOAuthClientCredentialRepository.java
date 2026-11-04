package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialId;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialStatus;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PostgresOAuthClientCredentialRepository implements OAuthClientCredentialRepository {
    @Override
    public Uni<Optional<OAuthClientCredential>> findById(UUID tenantId, OAuthClientCredentialId id) {
        return OAuthClientCredentialEntity.<OAuthClientCredentialEntity>find(
                "tenantId = ?1 and id = ?2", tenantId, id.value())
                .firstResult().map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<OAuthClientCredential>> findByClientId(UUID tenantId, String clientId) {
        return OAuthClientCredentialEntity.<OAuthClientCredentialEntity>find(
                "tenantId = ?1 and clientId = ?2", tenantId, clientId)
                .firstResult().map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Void> save(OAuthClientCredential credential) {
        return OAuthClientCredentialEntity.<OAuthClientCredentialEntity>find(
                "tenantId = ?1 and id = ?2", credential.tenantId(), credential.id().value())
                .firstResult().chain(existing -> {
                    OAuthClientCredentialEntity entity = existing != null ? existing : new OAuthClientCredentialEntity();
                    entity.id = credential.id().value();
                    entity.tenantId = credential.tenantId();
                    entity.applicationId = credential.applicationId();
                    entity.clientId = credential.clientId();
                    entity.secretHash = credential.secretHash();
                    entity.secretPrefix = credential.secretPrefix();
                    entity.scopes = credential.scopes().stream().sorted().collect(Collectors.joining(","));
                    entity.status = credential.status().name();
                    entity.credentialVersion = credential.credentialVersion();
                    entity.createdAt = credential.createdAt();
                    entity.expiresAt = credential.expiresAt();
                    entity.rotatedAt = credential.rotatedAt();
                    entity.revokedAt = credential.revokedAt();
                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    private OAuthClientCredential toDomain(OAuthClientCredentialEntity entity) {
        Set<String> scopes = Arrays.stream(entity.scopes.split(","))
                .filter(scope -> !scope.isBlank()).collect(Collectors.toUnmodifiableSet());
        return OAuthClientCredential.reconstitute(OAuthClientCredentialId.of(entity.id),
                entity.tenantId, entity.applicationId, entity.clientId, entity.secretHash,
                entity.secretPrefix, scopes, OAuthClientCredentialStatus.valueOf(entity.status),
                entity.credentialVersion, entity.createdAt, entity.expiresAt, entity.rotatedAt, entity.revokedAt);
    }
}
