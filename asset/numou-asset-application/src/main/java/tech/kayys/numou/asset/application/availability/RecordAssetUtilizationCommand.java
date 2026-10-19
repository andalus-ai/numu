package tech.kayys.numou.asset.application.availability;

import tech.kayys.numou.asset.domain.availability.AssetUtilizationType;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records an utilization observation (ASSET-26 §15).
 *
 * <p>{@code source}/{@code referenceId} form the idempotency key for external
 * ingestion, mirroring the meter readings of ASSET-21 §21.21.</p>
 */
public record RecordAssetUtilizationCommand(
        String tenantId,
        UUID assetId,
        Instant startsAt,
        Instant endsAt,
        AssetUtilizationType type,
        BigDecimal quantity,
        String unit,
        String source,
        String referenceId
) implements Command {
}