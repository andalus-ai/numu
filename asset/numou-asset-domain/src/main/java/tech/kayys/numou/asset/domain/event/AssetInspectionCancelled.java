package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a DRAFT/IN_PROGRESS inspection is cancelled. */
public record AssetInspectionCancelled(
        UUID eventId,
        Instant occurredAt,
        UUID inspectionId,
        UUID assetId
) implements DomainEvent {

    public AssetInspectionCancelled {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.inspection-cancelled";
    }
}
