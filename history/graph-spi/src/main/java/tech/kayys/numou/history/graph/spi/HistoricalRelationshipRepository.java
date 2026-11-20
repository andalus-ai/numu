package tech.kayys.numou.history.graph.spi;

import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalRelationship;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-aware persistence contract for historical relationship assertions.
 *
 * <p>tenantId must come from the authenticated server-side tenant context, never
 * from an untrusted request field. Reads return shared public assertions plus
 * assertions owned by that tenant. Writes through this contract are tenant-private;
 * shared-corpus ingestion uses a separately privileged workflow.</p>
 *
 * <p>saveTenantScoped appends an immutable revision. expectedRevision=0 creates
 * only when no tenant-owned revision exists; otherwise it performs a compare-and-
 * append against the latest revision. A false result means a concurrency conflict.</p>
 */
public interface HistoricalRelationshipRepository {
    Optional<HistoricalRelationship> findVisibleTo(UUID tenantId, HistoricalRelationshipId id);

    List<HistoricalRelationship> findBySubject(
            UUID tenantId, HistoricalEntityReference subject, int limit);

    List<HistoricalRelationship> findByObject(
            UUID tenantId, HistoricalEntityReference object, int limit);

    boolean saveTenantScoped(UUID tenantId, HistoricalRelationship relationship, long expectedRevision);
}
