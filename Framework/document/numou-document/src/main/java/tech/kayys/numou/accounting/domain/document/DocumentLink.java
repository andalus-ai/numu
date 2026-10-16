package tech.kayys.syirkah.accounting.domain.document;

import java.time.Instant;
import java.util.Objects;

/** Evidence relationship linking a document to a domain aggregate (e.g. Invoice, Journal). */
public record DocumentLink(
        DocumentLinkId id,
        DocumentId documentId,
        String aggregateType,
        String aggregateId,
        String relationship,
        Instant linkedAt
) {
    public DocumentLink {
        Objects.requireNonNull(id);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(relationship);
        Objects.requireNonNull(linkedAt);
    }
    public static DocumentLink create(DocumentId docId, String aggType, String aggId, String rel) {
        return new DocumentLink(DocumentLinkId.generate(), docId, aggType, aggId, rel, Instant.now());
    }
}
