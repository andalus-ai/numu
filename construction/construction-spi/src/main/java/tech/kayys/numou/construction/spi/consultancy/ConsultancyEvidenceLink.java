package tech.kayys.numou.construction.spi.consultancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Read model for a version-pinned document relationship; contains no document bytes. */
public record ConsultancyEvidenceLink(
        UUID linkId,
        UUID deliverableId,
        UUID documentId,
        UUID documentVersionId,
        String relationship,
        UUID linkedBy,
        Instant linkedAt) {
    public ConsultancyEvidenceLink {
        Objects.requireNonNull(linkId, "Link id is required");
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(documentId, "Document id is required");
        Objects.requireNonNull(documentVersionId, "Document version id is required");
        relationship = requireText(relationship, "Relationship");
        Objects.requireNonNull(linkedBy, "Actor id is required");
        Objects.requireNonNull(linkedAt, "Link timestamp is required");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
