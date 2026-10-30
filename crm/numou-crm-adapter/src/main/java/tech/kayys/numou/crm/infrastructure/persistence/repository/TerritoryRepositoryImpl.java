package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.crm.domain.repository.TerritoryRepository;
import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.TerritoryMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-isolated persistence adapter for CRM territories. */
@ApplicationScoped
public class TerritoryRepositoryImpl implements TerritoryRepository {

    private final TerritoryMapper mapper;
    private final TenantScopeProvider tenantScopeProvider;

    @Inject
    public TerritoryRepositoryImpl(TerritoryMapper mapper, TenantScopeProvider tenantScopeProvider) {
        this.mapper = mapper;
        this.tenantScopeProvider = tenantScopeProvider;
    }

    private UUID tenantId() {
        TenantScope scope = tenantScopeProvider.requireTenantScope();
        return scope.tenantId().value();
    }

    @Override
    public CompletionStage<Territory> save(Territory territory) {
        if (territory == null) {
            throw new IllegalArgumentException("territory is required");
        }
        UUID tenant = tenantId();
        TerritoryEntity mapped = mapper.toEntity(territory);
        mapped.tenantId = tenant;

        return Panache.withTransaction(() -> validateParent(mapped.parentTerritoryId, mapped.id, tenant)
                .flatMap(ignored -> TerritoryEntity.<TerritoryEntity>find(
                        "id = ?1 and tenantId = ?2", mapped.id, tenant).firstResult())
                .flatMap(existing -> {
                    if (existing == null) {
                        return mapped.<TerritoryEntity>persist().map(saved -> territory);
                    }
                    existing.name = mapped.name;
                    existing.description = mapped.description;
                    existing.parentTerritoryId = mapped.parentTerritoryId;
                    existing.territoryActive = mapped.territoryActive;
                    existing.active = mapped.active;
                    existing.updatedAt = mapped.updatedAt;
                    return Uni.createFrom().item(territory);
                }))
                .map(saved -> {
                    territory.clearEvents();
                    return saved;
                })
                .subscribe().asCompletionStage();
    }

    private Uni<Void> validateParent(UUID parentId, UUID territoryId, UUID tenant) {
        if (parentId == null) {
            return Uni.createFrom().voidItem();
        }
        if (parentId.equals(territoryId)) {
            return Uni.createFrom().failure(new IllegalArgumentException(
                    "A territory cannot be its own parent"));
        }
        return TerritoryEntity.count("id = ?1 and tenantId = ?2", parentId, tenant)
                .flatMap(count -> count == 1
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new SecurityException(
                                "Parent territory does not belong to the verified tenant")));
    }

    @Override
    public CompletionStage<Optional<Territory>> findById(TerritoryId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> TerritoryEntity.<TerritoryEntity>find(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .firstResult()
                .map(entity -> entity == null ? Optional.<Territory>empty()
                        : Optional.of(mapper.toDomain(entity))))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryId id) {
        UUID tenant = tenantId();
        return Panache.withSession(() -> TerritoryEntity.count(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Territory territory) {
        if (territory == null || territory.id() == null) {
            throw new IllegalArgumentException("territory ID is required");
        }
        return deleteById(territory.id());
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryId id) {
        UUID tenant = tenantId();
        return Panache.withTransaction(() -> TerritoryEntity.delete(
                        "id = ?1 and tenantId = ?2", id.getValue(), tenant)
                .map(ignored -> (Void) null))
                .subscribe().asCompletionStage();
    }
}
