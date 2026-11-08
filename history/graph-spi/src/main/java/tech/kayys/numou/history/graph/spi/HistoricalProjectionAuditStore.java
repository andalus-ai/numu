package tech.kayys.numou.history.graph.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bounded read contract over immutable canonical projection events. */
public interface HistoricalProjectionAuditStore {
    List<CanonicalRelationship> nextLatestPage(UUID tenantId, String afterRelationshipId, int limit);
    Optional<CanonicalRelationship> findLatest(UUID tenantId, UUID relationshipId);

    record CanonicalRelationship(UUID eventId, String eventType, int schemaVersion, UUID tenantId,
                                 UUID relationshipId, long revision, String eventKey,
                                 String relationshipKind, String subjectKind, String subjectId,
                                 String objectKind, String objectId, String payload, Instant occurredAt) {}
}
