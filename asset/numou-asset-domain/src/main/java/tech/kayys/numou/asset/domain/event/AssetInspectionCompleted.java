package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.inspection.AssetCondition;
import tech.kayys.numou.asset.domain.inspection.InspectionResult;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when an IN_PROGRESS inspection completes (ASSET-20 section 20.16). */
public record AssetInspectionCompleted(
        UUID eventId,
        Instant occurredAt,
        UUID inspectionId,
        UUID assetId,
        AssetCondition condition,
        InspectionResult result
) implements DomainEvent {

    public AssetInspectionCompleted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.inspection-completed";
    }
}
