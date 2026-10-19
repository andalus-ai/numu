package tech.kayys.numou.asset.application.meter;

import tech.kayys.numou.asset.domain.meter.MeterReading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Success payload for a recorded reading. */
public record MeterReadingResult(
        UUID readingId,
        UUID meterId,
        UUID assetId,
        BigDecimal value,
        String unit,
        Instant recordedAt,
        Instant occurredAt,
        String recordedBy,
        String type,
        String source,
        String sourceRef,
        boolean duplicate
) {

    public static MeterReadingResult from(MeterReading reading, boolean duplicate) {
        return new MeterReadingResult(
                reading.id().value(),
                reading.meterId(),
                reading.assetId(),
                reading.value(),
                reading.unit().name(),
                reading.recordedAt(),
                reading.occurredAt(),
                reading.recordedBy(),
                reading.type().name(),
                reading.source(),
                reading.sourceRef(),
                duplicate);
    }
}
