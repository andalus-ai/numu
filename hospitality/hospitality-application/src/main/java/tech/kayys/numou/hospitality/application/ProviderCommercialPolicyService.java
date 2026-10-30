package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.NightlyRateOverride;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.StayDateRestriction;
import tech.kayys.numou.hospitality.domain.StayRatePlan;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayCommercialPolicyRepository;

/** Provider-authorized management of accommodation rates and date-specific selling rules. */
public final class ProviderCommercialPolicyService {
    private final PropertyListingRepository listings;
    private final AccommodationUnitRepository units;
    private final ProviderAccessPolicy access;
    private final StayCommercialPolicyRepository policies;

    public ProviderCommercialPolicyService(PropertyListingRepository listings,
                                           AccommodationUnitRepository units,
                                           ProviderAccessPolicy access,
                                           StayCommercialPolicyRepository policies) {
        this.listings = Objects.requireNonNull(listings, "listings");
        this.units = Objects.requireNonNull(units, "units");
        this.access = Objects.requireNonNull(access, "access");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    public record UnitCommand(String tenantId, String actorId, String providerId,
                              UUID listingId, UUID unitId) { }

    public record SaveRatePlan(String tenantId, String actorId, String providerId,
                               UUID listingId, UUID unitId, StayRatePlan plan) { }

    public record SaveRateOverrides(String tenantId, String actorId, String providerId,
                                    UUID listingId, UUID unitId, UUID ratePlanId,
                                    List<NightlyRateOverride> overrides) { }

    public record SaveStayRestrictions(String tenantId, String actorId, String providerId,
                                       UUID listingId, UUID unitId,
                                       List<StayDateRestriction> restrictions) { }

    public Uni<StayRatePlan> saveRatePlan(SaveRatePlan command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId());
        StayRatePlan plan = Objects.requireNonNull(command.plan(), "plan");
        if (!command.listingId().equals(plan.listingId()) || !command.unitId().equals(plan.unitId())) {
            throw new IllegalArgumentException("rate plan must belong to the requested listing and unit");
        }
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.saveRatePlan(command.tenantId(), plan));
    }

    public Uni<Void> saveNightlyRateOverrides(SaveRateOverrides command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId());
        Objects.requireNonNull(command.ratePlanId(), "ratePlanId");
        List<NightlyRateOverride> overrides = List.copyOf(
                Objects.requireNonNull(command.overrides(), "overrides"));
        if (overrides.isEmpty()) return Uni.createFrom().voidItem();
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.findRatePlan(command.tenantId(), command.listingId(),
                        command.unitId(), command.ratePlanId()))
                .flatMap(found -> {
                    StayRatePlan plan = found.orElseThrow(ListingNotFoundException::new);
                    for (NightlyRateOverride override : overrides) {
                        if (!command.ratePlanId().equals(override.ratePlanId())
                                || override.date().isBefore(plan.validFrom())
                                || !override.date().isBefore(plan.validUntil())) {
                            return Uni.createFrom().failure(new IllegalArgumentException(
                                    "nightly override must target this rate plan within its validity interval"));
                        }
                    }
                    return policies.saveNightlyRateOverrides(command.tenantId(), command.listingId(),
                            command.unitId(), command.ratePlanId(), overrides);
                });
    }

    public Uni<Void> saveStayDateRestrictions(SaveStayRestrictions command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId());
        List<StayDateRestriction> restrictions = List.copyOf(
                Objects.requireNonNull(command.restrictions(), "restrictions"));
        if (restrictions.isEmpty()) return Uni.createFrom().voidItem();
        for (StayDateRestriction restriction : restrictions) {
            if (!command.listingId().equals(restriction.listingId())
                    || !command.unitId().equals(restriction.unitId())) {
                throw new IllegalArgumentException("restriction must belong to the requested listing and unit");
            }
        }
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.saveStayDateRestrictions(command.tenantId(),
                        command.listingId(), command.unitId(), restrictions));
    }

    public Uni<List<StayRatePlan>> listRatePlans(UnitCommand command) {
        Objects.requireNonNull(command, "command");
        requireContext(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId());
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.findRatePlans(command.tenantId(),
                        command.listingId(), command.unitId()));
    }

    public Uni<List<NightlyRateOverride>> listNightlyRateOverrides(UnitCommand command, UUID ratePlanId,
                                                                    LocalDate fromInclusive,
                                                                    LocalDate untilExclusive) {
        validateRange(fromInclusive, untilExclusive);
        Objects.requireNonNull(ratePlanId, "ratePlanId");
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.findRatePlan(command.tenantId(), command.listingId(),
                        command.unitId(), ratePlanId))
                .flatMap(found -> found.isEmpty()
                        ? Uni.createFrom().failure(new ListingNotFoundException())
                        : policies.findNightlyRateOverrides(command.tenantId(), command.listingId(),
                                command.unitId(), ratePlanId, fromInclusive, untilExclusive));
    }

    public Uni<List<StayDateRestriction>> listStayDateRestrictions(UnitCommand command,
                                                                     LocalDate fromInclusive,
                                                                     LocalDate untilExclusive) {
        validateRange(fromInclusive, untilExclusive);
        return authorizeAndResolveUnit(command.tenantId(), command.actorId(), command.providerId(),
                command.listingId(), command.unitId())
                .flatMap(ignored -> policies.findStayDateRestrictions(command.tenantId(),
                        command.listingId(), command.unitId(), fromInclusive, untilExclusive));
    }

    private Uni<AccommodationUnit> authorizeAndResolveUnit(String tenantId, String actorId, String providerId,
                                                            UUID listingId, UUID unitId) {
        return access.requireCanManageListings(tenantId, actorId, providerId)
                .flatMap(ignored -> listings.findById(tenantId, listingId))
                .flatMap(found -> {
                    if (found.isEmpty()) return Uni.createFrom().failure(new ListingNotFoundException());
                    PropertyListing listing = found.get();
                    if (!tenantId.equals(listing.tenantId()) || !providerId.equals(listing.providerId())
                            || listing.status() == ListingStatus.ARCHIVED) {
                        return Uni.createFrom().failure(new ListingNotFoundException());
                    }
                    return units.findById(tenantId, listingId, unitId).flatMap(foundUnit -> {
                        if (foundUnit.isEmpty() || !listingId.equals(foundUnit.get().listingId())) {
                            return Uni.createFrom().failure(new ListingNotFoundException());
                        }
                        return Uni.createFrom().item(foundUnit.get());
                    });
                });
    }

    private static void validateRange(LocalDate fromInclusive, LocalDate untilExclusive) {
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(untilExclusive, "untilExclusive");
        if (!untilExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("untilExclusive must be after fromInclusive");
        }
    }

    private static void requireContext(String tenantId, String actorId, String providerId,
                                       UUID listingId, UUID unitId) {
        requireText(tenantId, "tenantId");
        requireText(actorId, "actorId");
        requireText(providerId, "providerId");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
