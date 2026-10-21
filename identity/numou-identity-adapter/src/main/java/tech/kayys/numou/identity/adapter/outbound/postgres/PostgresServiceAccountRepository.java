package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.ServiceAccountRepository;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccountId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PostgresServiceAccountRepository implements ServiceAccountRepository {

    @Override
    public Uni<Optional<ServiceAccount>> findById(ServiceAccountId id) {
        return ServiceAccountEntity.<ServiceAccountEntity>findById(id.value())
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<ServiceAccount>> findByName(String name) {
        return ServiceAccountEntity.<ServiceAccountEntity>find("name", name)
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Boolean> existsByName(String name) {
        return ServiceAccountEntity.count("name", name).map(count -> count > 0);
    }

    @Override
    public Uni<List<ServiceAccount>> findAll() {
        return ServiceAccountEntity.<ServiceAccountEntity>listAll()
                .map(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Void> save(ServiceAccount serviceAccount) {
        return ServiceAccountEntity.<ServiceAccountEntity>findById(serviceAccount.id().value())
                .chain(existing -> {
                    ServiceAccountEntity entity = existing != null ? existing : new ServiceAccountEntity();
                    entity.id = serviceAccount.id().value();
                    entity.tenantId = serviceAccount.tenantId() != null ? serviceAccount.tenantId().value() : null;
                    entity.code = serviceAccount.name().toLowerCase(java.util.Locale.ROOT);
                    entity.name = serviceAccount.name();
                    entity.description = serviceAccount.description();
                    entity.ownerUserId = serviceAccount.ownerUserId() != null ? serviceAccount.ownerUserId().value() : null;
                    entity.status = serviceAccount.isEnabled() ? "ACTIVE" : "REVOKED";
                    entity.createdAt = serviceAccount.createdAt();
                    entity.revokedAt = serviceAccount.isEnabled() ? null : Instant.now();
                    entity.auditCreatedBy = serviceAccount.auditMeta().createdBy();
                    entity.auditUpdatedBy = serviceAccount.auditMeta().updatedBy();
                    entity.auditUpdatedAt = serviceAccount.auditMeta().updatedAt();

                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    private ServiceAccount toDomain(ServiceAccountEntity entity) {
        Instant createdAt = entity.createdAt != null ? entity.createdAt : Instant.now();
        return ServiceAccount.reconstitute(
                ServiceAccountId.of(entity.id),
                entity.tenantId != null ? TenantId.of(entity.tenantId) : null,
                entity.name,
                entity.description,
                entity.ownerUserId != null ? UserId.of(entity.ownerUserId) : null,
                "ACTIVE".equalsIgnoreCase(entity.status),
                createdAt,
                auditMetaFrom(entity.auditCreatedBy, createdAt, entity.auditUpdatedBy, entity.auditUpdatedAt)
        );
    }

    /** Null audit columns (pre-migration rows) yield a derived AuditMeta inside the domain. */
    private static tech.kayys.numou.foundation.domain.audit.AuditMeta auditMetaFrom(
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        if (createdBy == null || updatedAt == null) {
            return null;
        }
        return new tech.kayys.numou.foundation.domain.audit.AuditMeta(
                createdBy, createdAt, updatedBy != null ? updatedBy : createdBy, updatedAt
        );
    }
}
