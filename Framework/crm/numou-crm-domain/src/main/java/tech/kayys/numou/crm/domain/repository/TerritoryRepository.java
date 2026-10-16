package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.domain.repository.Repository;

/**
 * Repository port for {@link Territory} aggregates.
 */
public interface TerritoryRepository extends Repository<Territory, TerritoryId> {
}