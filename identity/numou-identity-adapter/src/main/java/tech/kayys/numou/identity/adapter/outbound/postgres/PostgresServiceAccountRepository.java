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
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class PostgresServiceAccountRepository implements ServiceAccountRepository {

    @Override
    public Uni<Optional<ServiceAccount>> findById(TenantId tenantId, ServiceAccountId id) {
        return ServiceAccountEntity.<ServiceAccountEntity>find(
                        "tenantId = ?1 and id = ?2", tenantId.value(), id.value())
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<ServiceAccount>> findByName(TenantId tenantId, String name) {
        return ServiceAccountEntity.<ServiceAccountEntity>find(
                        "tenantId = ?1 and code = ?2", tenantId.value(), normalizeCode(name))
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Boolean> existsByName(TenantId tenantId, String name) {
        return ServiceAccountEntity.count(
                "tenantId = ?1 and code = ?2", tenantId.value(), normalizeCode(name))
                .map(count -> count > 0);
    }

    @Override
    public Uni<List<ServiceAccount>> findAll(TenantId tenantId) {
        return ServiceAccountEntity.<ServiceAccountEntity>list(
                        "tenantId = ?1 order by createdAt desc, id asc", tenantId.value())
                .map(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Void> save(TenantId tenantId, ServiceAccount serviceAccount) {
        if (!tenantId.equals(serviceAccount.tenantId())) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Service account tenant does not match persistence scope"));
        }
        return ServiceAccountEntity.<ServiceAccountEntity>find(
                        "tenantId = ?1 and id = ?2", tenantId.value(), serviceAccount.id().value())
                .firstResult()
                .chain(existing -> {
                    ServiceAccountEntity entity = existing != null ? existing : new ServiceAccountEntity();
                    entity.id = serviceAccount.id().value();
                    entity.tenantId = tenantId.value();
                    entity.code = normalizeCode(serviceAccount.name());
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

    private static String normalizeCode(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private ServiceAccount toDomain(ServiceAccountEntity entity) {
        Instant createdAt = entity.createdAt != null ? entity.createdAt : Instant.now();
        return ServiceAccount.reconstitute(
                ServiceAccountId.of(entity.id),
                TenantId.of(entity.tenantId),
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
