package tech.kayys.numou.history.graph.application;

import java.util.List;
import java.util.UUID;

/** Read-only inspection contract for a non-active staging generation. */
public interface GenerationTargetedHistoricalProjectionInspectionPort extends HistoricalProjectionInspectionPort {
    java.util.Optional<ProjectedRelationship> findInGeneration(UUID tenantId, UUID generationId, UUID relationshipId);
    List<ProjectedRelationship> nextPageInGeneration(UUID tenantId, UUID generationId,
                                                      String afterRelationshipId, int limit);
}
