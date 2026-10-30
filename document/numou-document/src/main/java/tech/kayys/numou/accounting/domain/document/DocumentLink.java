package tech.kayys.numou.accounting.domain.document;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evidence relationship linking a document (optionally a pinned immutable version)
 * to a domain aggregate (e.g. Invoice, Journal or consultancy deliverable).
 */
public record DocumentLink(
        DocumentLinkId id,
        DocumentId documentId,
        DocumentVersionId documentVersionId,
        String aggregateType,
        String aggregateId,
        String relationship,
        UUID linkedBy,
        Instant linkedAt
) {
    public DocumentLink {
        Objects.requireNonNull(id);
        Objects.requireNonNull(documentId);
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (relationship == null || relationship.isBlank()) {
            throw new IllegalArgumentException("relationship must not be blank");
        }
        Objects.requireNonNull(linkedAt);
        aggregateType = aggregateType.trim();
        aggregateId = aggregateId.trim();
        relationship = relationship.trim();
    }

    /** Backwards-compatible constructor for version-aware links without actor attribution. */
    public DocumentLink(
            DocumentLinkId id, DocumentId documentId, DocumentVersionId documentVersionId,
            String aggregateType, String aggregateId, String relationship, Instant linkedAt) {
        this(id, documentId, documentVersionId, aggregateType, aggregateId, relationship, null, linkedAt);
    }

    /** Backwards-compatible constructor for legacy links without a pinned version. */
    public DocumentLink(
            DocumentLinkId id,
            DocumentId documentId,
            String aggregateType,
            String aggregateId,
            String relationship,
            Instant linkedAt) {
        this(id, documentId, null, aggregateType, aggregateId, relationship, null, linkedAt);
    }

    /** Creates a legacy link that does not pin a version. */
    public static DocumentLink create(DocumentId docId, String aggType, String aggId, String rel) {
        return new DocumentLink(DocumentLinkId.generate(), docId, null, aggType, aggId, rel, null, Instant.now());
    }

    /** Creates a link to a specific immutable document version. */
    public static DocumentLink create(
            DocumentId docId, DocumentVersionId versionId, String aggType, String aggId, String rel) {
        return new DocumentLink(
                DocumentLinkId.generate(), docId, Objects.requireNonNull(versionId),
                aggType, aggId, rel, null, Instant.now());
    }
    /** Creates a version-pinned evidence link with durable actor attribution. */
    public static DocumentLink create(
            DocumentId docId, DocumentVersionId versionId, String aggType,
            String aggId, String rel, UUID linkedBy) {
        return new DocumentLink(
                DocumentLinkId.generate(), docId, Objects.requireNonNull(versionId),
                aggType, aggId, rel, Objects.requireNonNull(linkedBy), Instant.now());
    }
}
