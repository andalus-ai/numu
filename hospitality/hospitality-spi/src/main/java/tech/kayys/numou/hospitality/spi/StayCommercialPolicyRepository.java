package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.NightlyRateOverride;
import tech.kayys.numou.hospitality.domain.StayDateRestriction;
import tech.kayys.numou.hospitality.domain.StayRatePlan;

/** Tenant-scoped persistence port for Hospitality-owned rates and stay restrictions. */
public interface StayCommercialPolicyRepository {
    Uni<StayRatePlan> saveRatePlan(String tenantId, StayRatePlan plan);

    Uni<Optional<StayRatePlan>> findRatePlan(String tenantId, UUID listingId, UUID unitId, UUID ratePlanId);

    Uni<List<StayRatePlan>> findRatePlans(String tenantId, UUID listingId, UUID unitId);

    /** Atomically upserts all overrides; an empty collection is a no-op. */
    Uni<Void> saveNightlyRateOverrides(String tenantId, UUID listingId, UUID unitId,
                                       UUID ratePlanId, List<NightlyRateOverride> overrides);

    Uni<List<NightlyRateOverride>> findNightlyRateOverrides(String tenantId, UUID listingId, UUID unitId,
                                                             UUID ratePlanId, LocalDate fromInclusive,
                                                             LocalDate untilExclusive);

    /** Atomically upserts all date restrictions; an empty collection is a no-op. */
    Uni<Void> saveStayDateRestrictions(String tenantId, UUID listingId, UUID unitId,
                                       List<StayDateRestriction> restrictions);

    Uni<List<StayDateRestriction>> findStayDateRestrictions(String tenantId, UUID listingId, UUID unitId,
                                                              LocalDate fromInclusive, LocalDate untilExclusive);
}
