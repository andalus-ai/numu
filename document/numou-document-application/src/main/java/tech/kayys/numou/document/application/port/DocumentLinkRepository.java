package tech.kayys.numou.document.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.document.DocumentLink;

import java.util.List;

/**
 * Tenant-scoped relationship store for documents and domain aggregates.
 *
 * <p>Links are append-only evidence relationships: implementations must not silently
 * retarget an existing link. Consumers should link a new document version explicitly
 * when the evidence changes.</p>
 */
public interface DocumentLinkRepository {
    Uni<Void> save(String tenantId, DocumentLink link);

    Uni<List<DocumentLink>> findByAggregate(
            String tenantId, String aggregateType, String aggregateId);
}
