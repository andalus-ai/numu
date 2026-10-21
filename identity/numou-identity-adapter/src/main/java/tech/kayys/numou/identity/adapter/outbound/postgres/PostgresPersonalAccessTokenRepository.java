package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.PersonalAccessTokenRepository;
import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.PersonalAccessTokenId;
import tech.kayys.numou.identity.domain.token.TokenStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostgresPersonalAccessTokenRepository implements PersonalAccessTokenRepository {

    @Override
    public Uni<Optional<PersonalAccessToken>> findById(PersonalAccessTokenId id) {
        return PersonalAccessTokenEntity.<PersonalAccessTokenEntity>findById(id.value())
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<PersonalAccessToken>> findByTokenHash(String tokenHash) {
        return PersonalAccessTokenEntity.<PersonalAccessTokenEntity>find("tokenHash", tokenHash)
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<List<PersonalAccessToken>> findByPrincipal(String principalType, UUID principalId) {
        return PersonalAccessTokenEntity.<PersonalAccessTokenEntity>find(
                        "principalType = ?1 and principalId = ?2", principalType, principalId)
                .list()
                .map(entities -> entities.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Void> save(PersonalAccessToken pat) {
        return PersonalAccessTokenEntity.<PersonalAccessTokenEntity>findById(pat.id().value())
                .chain(existing -> {
                    PersonalAccessTokenEntity entity = existing != null ? existing : new PersonalAccessTokenEntity();
                    entity.id = pat.id().value();
                    entity.principalType = pat.principalType();
                    entity.principalId = pat.principalId();
                    entity.label = pat.label();
                    entity.tokenHash = pat.tokenHash();
                    entity.displayPrefix = pat.displayPrefix();
                    entity.status = pat.status().name();
                    entity.createdAt = pat.createdAt();
                    entity.expiresAt = pat.expiresAt();
                    entity.lastUsedAt = pat.lastUsedAt();
                    entity.revokedAt = pat.revokedAt();
                    entity.auditCreatedBy = pat.auditMeta().createdBy();
                    entity.auditUpdatedBy = pat.auditMeta().updatedBy();
                    entity.auditUpdatedAt = pat.auditMeta().updatedAt();

                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    private PersonalAccessToken toDomain(PersonalAccessTokenEntity entity) {
        return PersonalAccessToken.reconstitute(
                PersonalAccessTokenId.of(entity.id),
                entity.principalType,
                entity.principalId,
                entity.label,
                entity.tokenHash,
                entity.displayPrefix,
                TokenStatus.valueOf(entity.status),
                entity.createdAt,
                entity.expiresAt,
                entity.lastUsedAt,
                entity.revokedAt,
                auditMetaFrom(entity)
        );
    }

    /** Null audit columns (pre-migration rows) yield a derived AuditMeta inside the domain. */
    private static tech.kayys.numou.foundation.domain.audit.AuditMeta auditMetaFrom(PersonalAccessTokenEntity entity) {
        if (entity.auditCreatedBy == null || entity.auditUpdatedAt == null || entity.createdAt == null) {
            return null;
        }
        return new tech.kayys.numou.foundation.domain.audit.AuditMeta(
                entity.auditCreatedBy,
                entity.createdAt,
                entity.auditUpdatedBy != null ? entity.auditUpdatedBy : entity.auditCreatedBy,
                entity.auditUpdatedAt
        );
    }
}
