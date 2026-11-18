package tech.kayys.numou.feedback.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Public integration boundary implemented by the host composition using the Document
 * capability's application contract. Implementations must resolve by tenant and exact version.
 */
public interface FeedbackDocumentPort {
    Optional<DocumentEvidence> findVersion(UUID tenantId, String documentId, String documentVersionId);

    record DocumentEvidence(
            String documentId,
            String documentVersionId,
            String fileName,
            String mediaType,
            long sizeBytes,
            String sha256,
            String lifecycleStatus) {
    }
}
