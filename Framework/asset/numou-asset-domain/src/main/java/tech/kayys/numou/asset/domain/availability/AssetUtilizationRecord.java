package tech.kayys.syirkah.asset.domain.availability;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable observation of how much an asset was actually used over a
 * window (ASSET-26 §15-16).
 *
 * <p>Utilization is never a counter on the {@code Asset} aggregate. It is
 * recorded as historical facts that a read model aggregates. The
 * {@code source}/{@code referenceId} pair is the stable idempotency key for
 * external ingestion (mirroring ASSET-21 meter readings).</p>
 */
public record AssetUtilizationRecord(
        AssetUtilizationRecordId id,
        String tenantId,
        UUID assetId,
        Instant startsAt,
        Instant endsAt,
        AssetUtilizationType type,
        BigDecimal quantity,
        String unit,
        String source,
        String referenceId,
        Instant occurredAt
) {

    public AssetUtilizationRecord {
        Objects.requireNonNull(id, "id cannot be null");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(startsAt, "startsAt cannot be null");
        Objects.requireNonNull(endsAt, "endsAt cannot be null");
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessRuleViolation("Utilization endsAt must be strictly after startsAt");
        }
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        if (quantity.signum() < 0) {
            throw new BusinessRuleViolation("Utilization quantity cannot be negative");
        }
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
    }

    public static AssetUtilizationRecord record(
            AssetUtilizationRecordId id,
            String tenantId,
            UUID assetId,
            Instant startsAt,
            Instant endsAt,
            AssetUtilizationType type,
            BigDecimal quantity,
            String unit,
            String source,
            String referenceId,
            Instant occurredAt) {
        return new AssetUtilizationRecord(
                id, tenantId, assetId, startsAt, endsAt, type, quantity, unit, source, referenceId, occurredAt);
    }

    /** Duration of the observed window in seconds. */
    public long durationSeconds() {
        return endsAt.getEpochSecond() - startsAt.getEpochSecond();
    }
}