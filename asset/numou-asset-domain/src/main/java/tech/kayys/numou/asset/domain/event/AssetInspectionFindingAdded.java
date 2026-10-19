package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.inspection.finding.FindingSeverity;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a finding is recorded on an inspection (ASSET-20 section 20.17). */
public record AssetInspectionFindingAdded(
        UUID eventId,
        Instant occurredAt,
        UUID inspectionId,
        UUID assetId,
        UUID findingId,
        FindingSeverity severity
) implements DomainEvent {

    public AssetInspectionFindingAdded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(findingId, "findingId cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.inspection-finding-added";
    }
}
