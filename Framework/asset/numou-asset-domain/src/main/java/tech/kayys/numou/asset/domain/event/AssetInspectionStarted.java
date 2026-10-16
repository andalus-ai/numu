package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a DRAFT inspection transitions to IN_PROGRESS. */
public record AssetInspectionStarted(
        UUID eventId,
        Instant occurredAt,
        UUID inspectionId,
        UUID assetId
) implements DomainEvent {

    public AssetInspectionStarted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.inspection-started";
    }
}
