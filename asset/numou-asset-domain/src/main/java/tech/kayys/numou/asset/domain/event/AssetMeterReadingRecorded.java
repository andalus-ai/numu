package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a meter reading is recorded (ASSET-21 §21.22).
 *
 * <p>{@code occurredAt} is when the system recorded the event;
 * {@code recordedAt} is when the measurement occurred.</p>
 */
public record AssetMeterReadingRecorded(
        UUID eventId,
        Instant occurredAt,
        UUID meterId,
        UUID assetId,
        BigDecimal value,
        MeterUnit unit,
        Instant recordedAt
) implements DomainEvent {

    public AssetMeterReadingRecorded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(meterId, "meterId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
        Objects.requireNonNull(recordedAt, "recordedAt cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.meter-reading-recorded";
    }
}
