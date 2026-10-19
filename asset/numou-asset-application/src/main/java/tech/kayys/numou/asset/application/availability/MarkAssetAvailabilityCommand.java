package tech.kayys.numou.asset.application.availability;

import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.foundation.application.command.Command;

import java.time.Instant;
import java.util.UUID;

/**
 * Marks an asset available / unavailable for a window (ASSET-26 §10).
 *
 * <p>{@code endsAt} may be {@code null} to express an open-ended state.
 * {@code startsAt} defaults to the domain clock when omitted.</p>
 */
public record MarkAssetAvailabilityCommand(
        String tenantId,
        UUID assetId,
        AssetAvailabilityType type,
        AssetAvailabilityReason reason,
        Instant startsAt,
        Instant endsAt,
        String referenceId,
        String notes
) implements Command {
}