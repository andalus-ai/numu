package tech.kayys.numou.asset.application.meter;

import tech.kayys.numou.asset.domain.meter.AssetMeter;
import tech.kayys.numou.asset.domain.meter.AssetMeterId;
import tech.kayys.numou.asset.domain.meter.MeterBehavior;
import tech.kayys.numou.asset.domain.meter.MeterReadingType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Success payload for meter use cases. */
public record MeterResult(
        UUID meterId,
        UUID assetId,
        String type,
        String unit,
        String behavior,
        String name,
        UUID replacementOf,
        boolean active
) {

    public static MeterResult from(AssetMeter meter) {
        return new MeterResult(
                meter.id().value(),
                meter.assetId(),
                meter.type().name(),
                meter.unit().name(),
                meter.behavior().name(),
                meter.name(),
                meter.replacementOf(),
                meter.active());
    }
}
