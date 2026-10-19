package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when an availability period is recorded (ASSET-26 §14).
 *
 * <p>A single event covers both polarities via {@link AssetAvailabilityType};
 * consumers that only care about the state transition can read
 * {@code type == UNAVAILABLE}. Domain events carry no tenant/correlation
 * metadata — the messaging layer adds that (§14).</p>
 */
public record AssetAvailabilityMarked(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        UUID periodId,
        AssetAvailabilityType type,
        AssetAvailabilityReason reason,
        Instant startsAt,
        Instant endsAt,
        String referenceId
) implements DomainEvent {

    public AssetAvailabilityMarked {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(periodId, "periodId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(startsAt, "startsAt cannot be null");
    }

    @Override
    public String eventType() {
        return type == AssetAvailabilityType.UNAVAILABLE
                ? "asset.asset-unavailable"
                : "asset.asset-available";
    }
}