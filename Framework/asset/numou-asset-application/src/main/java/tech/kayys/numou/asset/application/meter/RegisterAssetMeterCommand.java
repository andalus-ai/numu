package tech.kayys.numou.asset.application.meter;

import tech.kayys.numou.asset.domain.meter.MeterBehavior;
import tech.kayys.numou.asset.domain.meter.MeterType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.UUID;

/** Registers a meter definition for an asset (ASSET-21 §21.6). */
public record RegisterAssetMeterCommand(
        String tenantId,
        UUID assetId,
        MeterType type,
        MeterUnit unit,
        MeterBehavior behavior,
        String name,
        UUID replacementOf
) implements Command {
}
