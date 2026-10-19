package tech.kayys.numou.asset.application.availability;

import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriod;

import java.time.Instant;
import java.util.UUID;

/** Success payload for a recorded availability period (ASSET-26 §10). */
public record AvailabilityResult(
        UUID periodId,
        UUID assetId,
        String type,
        String reason,
        Instant startsAt,
        Instant endsAt,
        boolean open,
        String referenceId
) {

    public static AvailabilityResult from(AssetAvailabilityPeriod period) {
        return new AvailabilityResult(
                period.id().value(),
                period.assetId(),
                period.type().name(),
                period.reason().name(),
                period.startsAt(),
                period.endsAt(),
                period.isOpen(),
                period.referenceId());
    }
}