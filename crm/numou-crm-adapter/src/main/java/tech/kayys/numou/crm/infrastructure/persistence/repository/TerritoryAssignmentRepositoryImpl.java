package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.repository.TerritoryAssignmentRepository;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.AccountEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryAssignmentEntity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.TerritoryAssignmentMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-isolated persistence adapter for territory-to-account assignments. */
@ApplicationScoped
public class TerritoryAssignmentRepositoryImpl implements TerritoryAssignmentRepository {

    private final TerritoryAssignmentMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public TerritoryAssignmentRepositoryImpl(TerritoryAssignmentMapper mapper,
            TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        TenantScope scope = tenantScopeProvider.requireTenantScope();
        return scope.tenantId().value();
    }

    @Override
    public CompletionStage<TerritoryAssignment> save(TerritoryAssignment assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment is required");
        }
        UUID tenant = tenantId();
        TerritoryAssignmentEntity mapped = mapper.toEntity(assignment);
        mapped.tenantId = tenant;

        return Panache.withTransaction(() -> validateReferences(mapped, tenant)
                .flatMap(ignored -> TerritoryAssignmentEntity.<TerritoryAssignmentEntity>find(
                        "id = ?1 and tenantId = ?2", mapped.id, tenant).firstResult())
                .flatMap(existing -> {
                    if (existing == null) {
                        return mapped.<TerritoryAssignmentEntity>persist().map(saved -> assignment);
                    }
                    if (!existing.territoryId.equals(mapped.territoryId)
                            || !existing.accountId.equals(mapped.accountId)) {
                        return Uni.createFrom().failure(new SecurityException(
                                "An assignment cannot be moved to another territory or account"));
                    }
                    existing.origin = mapped.origin;
                    existing.status = mapped.status;
                    existing.assignedByUserId = mapped.assignedByUserId;
                    existing.assignedAt = mapped.assignedAt;
                    existing.endedAt = mapped.endedAt;
                    existing.active = mapped.active;
                    existing.updatedAt = mapped.updatedAt;
                    return Uni.createFrom().item(assignment);
                }))
                .map(saved -> {
                    assignment.clearEvents();
                    return saved;
                })
                .subscribe().asCompletionStage();
    }

    private Uni<Void> validateReferences(TerritoryAssignmentEntity assignment, UUID tenant) {
        return TerritoryEntity.count("id = ?1 and tenantId = ?2", assignment.territoryId, tenant)
                .flatMap(territoryCount -> {
                    if (territoryCount != 1) {
                        return Uni.createFrom().failure(new SecurityException(
                                "Territory does not belong to the verified tenant"));
                    }
                    return AccountEntity.count("id = ?1 and tenantId = ?2", assignment.accountId, tenant)
                            .flatMap(accountCount -> accountCount == 1
                                    ? Uni.createFrom().voidItem()
                                    : Uni.createFrom().failure(new SecurityException(
                                            "Account does not belong to the verified tenant")));
                });
    }

    @Override
    public CompletionStage<Optional<TerritoryAssignment>> findById(TerritoryAssignmentId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> TerritoryAssignmentEntity.<TerritoryAssignmentEntity>find(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .firstResult()
                .map(entity -> entity == null ? Optional.<TerritoryAssignment>empty()
                        : Optional.of(mapper.toDomain(entity))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryAssignmentId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> TerritoryAssignmentEntity.count(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(TerritoryAssignment assignment) {
        if (assignment == null || assignment.id() == null) {
            throw new IllegalArgumentException("assignment ID is required");
        }
        return deleteById(assignment.id());
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryAssignmentId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> TerritoryAssignmentEntity.delete(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(ignored -> (Void) null))
                .subscribe().asCompletionStage();
    }
}
