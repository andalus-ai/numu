package tech.kayys.numou.construction.spi.consultancy;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.UUID;

/**
 * Port for referencing immutable document evidence from consultancy deliverables.
 *
 * <p>Construction owns the deliverable and authorization decision; the Document
 * capability owns document/version identity and link persistence. Implementations
 * must delegate to the public Document application contract rather than its tables
 * or adapter entities. This port deliberately does not transport file bytes.</p>
 *
 * <p>Implementations must preserve tenant scope, pin the requested document version,
 * and provide idempotent attach semantics for the same relationship. This port alone
 * does not make cross-capability writes atomic; orchestration must document and handle
 * partial failure until a shared transaction or outbox-based protocol is available.</p>
 */
public interface ConsultancyEvidenceLinkStore {
    Uni<ConsultancyEvidenceLink> attach(
            String tenantId,
            UUID deliverableId,
            UUID documentId,
            UUID documentVersionId,
            String relationship,
            UUID actorId);

    Uni<List<ConsultancyEvidenceLink>> findByDeliverable(
            String tenantId,
            UUID deliverableId);
}
