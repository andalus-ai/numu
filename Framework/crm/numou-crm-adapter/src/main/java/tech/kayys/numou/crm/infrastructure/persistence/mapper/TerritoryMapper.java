package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryEntity;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between {@link Territory} domain and persistence entities.
 */
@ApplicationScoped
public class TerritoryMapper {

    public TerritoryEntity toEntity(Territory territory) {
        TerritoryEntity entity = new TerritoryEntity();
        entity.id = territory.id().getValue();
        entity.name = territory.name();
        entity.description = territory.description();
        entity.parentTerritoryId = territory.parentTerritoryId() == null
                ? null : territory.parentTerritoryId().getValue();
        entity.territoryActive = territory.isActive();
        entity.active = true;
        entity.createdAt = territory.getCreatedAt();
        entity.updatedAt = territory.getUpdatedAt();
        entity.version = (long) territory.getVersion();
        return entity;
    }

    public Territory toDomain(TerritoryEntity entity) {
        Territory territory = Territory.restore(
                TerritoryId.of(entity.id),
                entity.name,
                entity.description,
                entity.parentTerritoryId == null ? null : TerritoryId.of(entity.parentTerritoryId),
                entity.territoryActive);
        territory.setCreatedAt(entity.createdAt);
        territory.setUpdatedAt(entity.updatedAt);
        territory.setVersion(entity.version != null ? entity.version.intValue() : 0);
        return territory;
    }
}
