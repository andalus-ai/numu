package tech.kayys.numou.asset.application.availability;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityPeriod;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.repository.AssetAvailabilityRepository;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds the availability read model (ASSET-26 §18).
 *
 * <p>Current state resolution: an explicit {@code UNAVAILABLE} period covering
 * "now" wins over an enclosing {@code AVAILABLE} window (the maintenance
 * override). With no covering period the asset falls back to its lifecycle
 * status.</p>
 */
public class GetAssetAvailabilityHandler {

    private final AssetRepository assets;
    private final AssetAvailabilityRepository availability;
    private final DomainClock clock;

    public GetAssetAvailabilityHandler(
            AssetRepository assets, AssetAvailabilityRepository availability, DomainClock clock) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<AssetAvailabilityView> handle(GetAssetAvailabilityQuery query) {
        return Uni.createFrom()
                .completionStage(() -> assets.findByTenantAndId(
                        query.tenantId(), AssetId.of(query.assetId())))
                .map(opt -> opt.orElseThrow(() -> new ApplicationErrorException(
                        ApplicationError.of("asset.not-found", "Asset not found"))))
                .flatMap(asset -> Uni.createFrom()
                        .completionStage(() -> availability.findByAssetId(
                                query.tenantId(), query.assetId(), null, null))
                        .map(periods -> build(asset, periods, clock.now())));
    }

    static AssetAvailabilityView build(Asset asset, List<AssetAvailabilityPeriod> periods, Instant now) {
        AssetAvailabilityPeriod unavailable = latestCovering(periods, now, AssetAvailabilityType.UNAVAILABLE);
        AssetAvailabilityPeriod available = latestCovering(periods, now, AssetAvailabilityType.AVAILABLE);

        boolean isAvailable;
        Instant availableFrom = null;
        Instant unavailableFrom = null;
        Instant unavailableUntil = null;
        AssetAvailabilityReason reason = null;

        if (unavailable != null) {
            isAvailable = false;
            unavailableFrom = unavailable.startsAt();
            unavailableUntil = unavailable.endsAt();
            reason = unavailable.reason();
        } else if (available != null) {
            isAvailable = true;
            availableFrom = available.startsAt();
        } else {
            isAvailable = asset.status().isOperational();
        }

        return new AssetAvailabilityView(
                asset.id().value(),
                asset.tenantId(),
                asset.assetNumber(),
                asset.name(),
                asset.status(),
                isAvailable,
                availableFrom,
                unavailableFrom,
                unavailableUntil,
                reason);
    }

    private static AssetAvailabilityPeriod latestCovering(
            List<AssetAvailabilityPeriod> periods, Instant now, AssetAvailabilityType type) {
        AssetAvailabilityPeriod found = null;
        for (AssetAvailabilityPeriod period : periods) {
            if (period.type() == type && period.covers(now)
                    && (found == null || period.startsAt().isAfter(found.startsAt()))) {
                found = period;
            }
        }
        return found;
    }
}