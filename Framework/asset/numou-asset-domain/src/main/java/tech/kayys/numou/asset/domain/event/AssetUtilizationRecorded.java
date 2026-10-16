package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.availability.AssetUtilizationType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a utilization observation is recorded (ASSET-26 §15, §17). */
public record AssetUtilizationRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        UUID utilizationId,
        AssetUtilizationType type,
        BigDecimal quantity,
        String unit,
        Instant startsAt,
        Instant endsAt
) implements DomainEvent {

    public AssetUtilizationRecorded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(utilizationId, "utilizationId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(startsAt, "startsAt cannot be null");
        Objects.requireNonNull(endsAt, "endsAt cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.utilization-recorded";
    }
}