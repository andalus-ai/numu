package tech.kayys.numou.history.graph.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only inspection contract for a disposable graph projection. */
public interface HistoricalProjectionInspectionPort {
    Optional<ProjectedRelationship> find(UUID tenantId, UUID relationshipId);
    List<ProjectedRelationship> nextPage(UUID tenantId, String afterRelationshipId, int limit);

    record ProjectedRelationship(UUID relationshipId, long revision, String eventKey,
                                 String relationshipKind, String subjectKind, String subjectId,
                                 String objectKind, String objectId, String payloadHash) {}
}
