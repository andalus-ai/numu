package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.availability.AssetAvailabilityPeriod;
import tech.kayys.syirkah.asset.domain.availability.AssetAvailabilityPeriodId;
import tech.kayys.syirkah.asset.domain.availability.AssetAvailabilityType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped outbound port for availability periods (ASSET-26 §8).
 *
 * <p>Tenant is always structural: it is part of every method signature so a
 * period can never be read or written across tenant boundaries.</p>
 */
public interface AssetAvailabilityRepository {

    CompletionStage<AssetAvailabilityPeriod> save(String tenantId, AssetAvailabilityPeriod period);

    CompletionStage<Optional<AssetAvailabilityPeriod>> findById(
            String tenantId, AssetAvailabilityPeriodId id);

    CompletionStage<List<AssetAvailabilityPeriod>> findByAssetId(
            String tenantId, UUID assetId, Instant from, Instant to);

    /**
     * Returns {@code true} when a period of the same {@code type} for the asset
     * overlaps the window. Cross-type overlaps (e.g. an unmet unavailability
     * inside an availability window) are legitimate and must NOT be reported as
     * conflicts (§7, §13 of ASSET-26).
     */
    CompletionStage<Boolean> hasOverlap(
            String tenantId, UUID assetId, AssetAvailabilityType type, Instant startsAt, Instant endsAt);

    /** The currently open (no {@code endsAt}) period for an asset, if any. */
    CompletionStage<Optional<AssetAvailabilityPeriod>> findOpen(String tenantId, UUID assetId);
}