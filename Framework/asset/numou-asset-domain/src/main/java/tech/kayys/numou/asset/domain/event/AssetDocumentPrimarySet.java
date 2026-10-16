package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.document.AssetDocumentType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when the primary document of a type is (re)designated (ASSET-24 §27). */
public record AssetDocumentPrimarySet(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        UUID documentId,
        AssetDocumentType type
) implements DomainEvent {

    public AssetDocumentPrimarySet {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.document-primary-set";
    }
}
