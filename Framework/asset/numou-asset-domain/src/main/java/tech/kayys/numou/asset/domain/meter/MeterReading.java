package tech.kayys.numou.asset.domain.meter;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable observation of a meter at a point in time (ASSET-21 §21.7, §21.10).
 *
 * <p>{@code recordedAt} is when the measurement occurred; {@code occurredAt}
 * is when the system recorded the event (§21.22). {@code source}/{@code sourceRef}
 * is the stable idempotency key for external ingestion (§21.21).</p>
 */
public record MeterReading(
        MeterReadingId id,
        String tenantId,
        UUID meterId,
        UUID assetId,
        BigDecimal value,
        MeterUnit unit,
        Instant recordedAt,
        Instant occurredAt,
        String recordedBy,
        MeterReadingType type,
        String source,
        String sourceRef
) {

    public MeterReading {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(meterId, "meterId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
        Objects.requireNonNull(recordedAt, "recordedAt cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    /** Factory enforcing the one-canonical-unit rule (§21.16). */
    public static MeterReading record(AssetMeter meter, MeterReadingId id, BigDecimal value, MeterUnit unit,
            Instant recordedAt, Instant occurredAt, String recordedBy, MeterReadingType type,
            String source, String sourceRef) {
        Objects.requireNonNull(meter, "meter cannot be null");
        if (unit != meter.unit()) {
            throw new BusinessRuleViolation(
                    "Reading unit " + unit + " does not match meter canonical unit " + meter.unit());
        }
        return new MeterReading(id, meter.tenantId(), meter.id().value(), meter.assetId(),
                value, unit, recordedAt, occurredAt, recordedBy,
                type == null ? MeterReadingType.NORMAL : type, source, sourceRef);
    }

    /**
     * Validates a monotonic corridor rule: {@code previous ≤ value ≤ next}
     * using chronological neighbours (§21.17). Non-monotonic meters always pass.
     */
    public static void validateCorridor(AssetMeter meter, BigDecimal value,
            Optional<MeterReading> previous, Optional<MeterReading> next) {
        Objects.requireNonNull(meter, "meter cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        if (!meter.isMonotonic()) {
            return;
        }
        if (previous.isPresent() && value.compareTo(previous.get().value()) < 0) {
            throw new BusinessRuleViolation(
                    "Meter reading cannot be lower than previous reading");
        }
        if (next.isPresent() && value.compareTo(next.get().value()) > 0) {
            throw new BusinessRuleViolation(
                    "Meter reading cannot be higher than next reading");
        }
    }
}
