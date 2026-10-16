package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.foundation.domain.repository.Repository;

/**
 * Repository port for {@link TerritoryAssignment} aggregates.
 */
public interface TerritoryAssignmentRepository extends Repository<TerritoryAssignment, TerritoryAssignmentId> {
}