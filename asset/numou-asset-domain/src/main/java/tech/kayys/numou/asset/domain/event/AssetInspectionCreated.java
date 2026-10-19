package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.inspection.InspectionType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when an inspection is created (DRAFT). */
public record AssetInspectionCreated(
        UUID eventId,
        Instant occurredAt,
        UUID inspectionId,
        UUID assetId,
        InspectionType type
) implements DomainEvent {

    public AssetInspectionCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.inspection-created";
    }
}
