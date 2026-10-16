package tech.kayys.syirkah.asset.domain.maintenance.schedule;

import tech.kayys.syirkah.asset.domain.meter.MeterType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Maintenance-cycle baseline for one meter type (ASSET-22 §11).
 *
 * <p>Only the cycle baseline is stored, never the full meter history.</p>
 */
public record MeterScheduleBaseline(
        MeterType meterType,
        BigDecimal baselineValue,
        Instant baselineRecordedAt
) {
    public MeterScheduleBaseline {
        Objects.requireNonNull(meterType, "meterType cannot be null");
        Objects.requireNonNull(baselineValue, "baselineValue cannot be null");
        Objects.requireNonNull(baselineRecordedAt, "baselineRecordedAt cannot be null");
    }
}
