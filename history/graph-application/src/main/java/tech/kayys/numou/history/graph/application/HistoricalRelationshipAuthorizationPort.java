package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipId;

import java.util.UUID;

/**
 * Resource-level authorization boundary. Implementations must fail closed and
 * must validate existence and access to both endpoint records, not merely possession of their IDs.
 */
public interface HistoricalRelationshipAuthorizationPort {
    void requireCanReadRelationship(UUID tenantId, HistoricalRelationshipId relationshipId);
    void requireCanCreateRelationship(UUID tenantId);
    void requireCanReviseRelationship(UUID tenantId, HistoricalRelationshipId relationshipId);
    void requireCanReferenceEntity(UUID tenantId, HistoricalEntityReference reference);
}
