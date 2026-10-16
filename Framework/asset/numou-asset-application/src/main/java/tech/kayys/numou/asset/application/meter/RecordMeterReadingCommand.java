package tech.kayys.numou.asset.application.meter;

import tech.kayys.numou.asset.domain.meter.MeterReadingType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a reading (ASSET-21 §21.14).
 *
 * <p>{@code recordedAt} is the measurement time; when {@code null} the handler
 * defaults it to the domain clock.</p>
 */
public record RecordMeterReadingCommand(
        String tenantId,
        UUID assetId,
        UUID meterId,
        BigDecimal value,
        MeterUnit unit,
        Instant recordedAt,
        String recordedBy,
        MeterReadingType type,
        String source,
        String sourceRef
) implements Command {
}
