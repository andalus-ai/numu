package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.TerritoryAssignmentEntity;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between {@link TerritoryAssignment} domain and persistence entities.
 */
@ApplicationScoped
public class TerritoryAssignmentMapper {

    public TerritoryAssignmentEntity toEntity(TerritoryAssignment assignment) {
        TerritoryAssignmentEntity entity = new TerritoryAssignmentEntity();
        entity.id = assignment.id().getValue();
        entity.territoryId = assignment.territoryId().getValue();
        entity.accountId = assignment.accountId().getValue();
        entity.origin = assignment.origin();
        entity.status = assignment.status();
        entity.assignedByUserId = assignment.assignedByUserId();
        entity.assignedAt = assignment.assignedAt();
        entity.endedAt = assignment.endedAt();
        entity.active = true;
        entity.createdAt = assignment.getCreatedAt();
        entity.updatedAt = assignment.getUpdatedAt();
        entity.version = (long) assignment.getVersion();
        return entity;
    }

    public TerritoryAssignment toDomain(TerritoryAssignmentEntity entity) {
        TerritoryAssignment assignment = TerritoryAssignment.restore(
                TerritoryAssignmentId.of(entity.id),
                TerritoryId.of(entity.territoryId),
                AccountId.of(entity.accountId),
                entity.origin,
                entity.status,
                entity.assignedByUserId,
                entity.assignedAt,
                entity.endedAt);
        assignment.setCreatedAt(entity.createdAt);
        assignment.setUpdatedAt(entity.updatedAt);
        assignment.setVersion(entity.version != null ? entity.version.intValue() : 0);
        return assignment;
    }
}
