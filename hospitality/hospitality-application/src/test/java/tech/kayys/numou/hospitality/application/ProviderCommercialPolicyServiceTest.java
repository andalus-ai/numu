package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.NightlyRateOverride;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.domain.StayDateRestriction;
import tech.kayys.numou.hospitality.domain.StayRatePlan;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayCommercialPolicyRepository;

class ProviderCommercialPolicyServiceTest {
    private static final String TENANT = "tenant-a";
    private static final String PROVIDER = "provider-a";
    private static final String ACTOR = "actor-a";
    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2031, 1, 1);
    private static final LocalDate UNTIL = LocalDate.of(2032, 1, 1);

    @Test
    void persistsRatePlanOnlyAfterAuthorizationAndOwnershipChecks() {
        RecordingPolicies policies = new RecordingPolicies();
        ProviderCommercialPolicyService service = service(listing(PROVIDER), unit(), policies,
                (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());
        StayRatePlan plan = plan();

        StayRatePlan saved = service.saveRatePlan(new ProviderCommercialPolicyService.SaveRatePlan(
                TENANT, ACTOR, PROVIDER, LISTING_ID, UNIT_ID, plan)).await().indefinitely();

        assertThat(saved).isEqualTo(plan);
        assertThat(policies.savedPlan).isEqualTo(plan);
    }

    @Test
    void doesNotPersistWhenProviderAuthorizationFails() {
        RecordingPolicies policies = new RecordingPolicies();
        ProviderCommercialPolicyService service = service(listing(PROVIDER), unit(), policies,
                (tenantId, actorId, providerId) -> Uni.createFrom().failure(new SecurityException("denied")));

        assertThatThrownBy(() -> service.saveRatePlan(new ProviderCommercialPolicyService.SaveRatePlan(
                TENANT, ACTOR, PROVIDER, LISTING_ID, UNIT_ID, plan())).await().indefinitely())
                .isInstanceOf(SecurityException.class);
        assertThat(policies.savedPlan).isNull();
    }

    @Test
    void hidesListingOwnedByAnotherProvider() {
        RecordingPolicies policies = new RecordingPolicies();
        ProviderCommercialPolicyService service = service(listing("provider-b"), unit(), policies,
                (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        assertThatThrownBy(() -> service.saveRatePlan(new ProviderCommercialPolicyService.SaveRatePlan(
                TENANT, ACTOR, PROVIDER, LISTING_ID, UNIT_ID, plan())).await().indefinitely())
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(policies.savedPlan).isNull();
    }

    @Test
    void validatesOverrideScopeAndRatePlanValidityBeforePersistence() {
        RecordingPolicies policies = new RecordingPolicies();
        StayRatePlan plan = plan();
        policies.plan = Optional.of(plan);
        ProviderCommercialPolicyService service = service(listing(PROVIDER), unit(), policies,
                (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());
        NightlyRateOverride outside = new NightlyRateOverride(plan.id(), UNTIL, new BigDecimal("600000"));

        assertThatThrownBy(() -> service.saveNightlyRateOverrides(
                new ProviderCommercialPolicyService.SaveRateOverrides(TENANT, ACTOR, PROVIDER,
                        LISTING_ID, UNIT_ID, plan.id(), List.of(outside))).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(policies.savedOverrides).isEmpty();
    }

    @Test
    void validatesRestrictionUnitScopeBeforePersistence() {
        RecordingPolicies policies = new RecordingPolicies();
        ProviderCommercialPolicyService service = service(listing(PROVIDER), unit(), policies,
                (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());
        StayDateRestriction foreign = new StayDateRestriction(LISTING_ID, UUID.randomUUID(),
                FROM, 1, 0, false, false, true);

        assertThatThrownBy(() -> service.saveStayDateRestrictions(
                new ProviderCommercialPolicyService.SaveStayRestrictions(TENANT, ACTOR, PROVIDER,
                        LISTING_ID, UNIT_ID, List.of(foreign))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(policies.savedRestrictions).isEmpty();
    }

    private static ProviderCommercialPolicyService service(PropertyListing listing, AccommodationUnit unit,
                                                             RecordingPolicies policies,
                                                             ProviderAccessPolicy access) {
        PropertyListingRepository listingRepository = new PropertyListingRepository() {
            @Override public Uni<PropertyListing> save(String tenantId, PropertyListing value) {
                return Uni.createFrom().item(value);
            }
            @Override public Uni<Optional<PropertyListing>> findById(String tenantId, UUID id) {
                return Uni.createFrom().item(TENANT.equals(tenantId) && LISTING_ID.equals(id)
                        ? Optional.of(listing) : Optional.empty());
            }
            @Override public Uni<List<PropertyListing>> findByProvider(String tenantId, String providerId) {
                return Uni.createFrom().item(List.of());
            }
            @Override public Uni<List<PropertyListing>> findPublished(String tenantId) {
                return Uni.createFrom().item(List.of());
            }
            @Override public Uni<Boolean> existsById(String tenantId, UUID id) {
                return Uni.createFrom().item(false);
            }
        };
        AccommodationUnitRepository unitRepository = (tenantId, listingId, unitId) ->
                Uni.createFrom().item(TENANT.equals(tenantId) && LISTING_ID.equals(listingId)
                        && UNIT_ID.equals(unitId) ? Optional.of(unit) : Optional.empty());
        return new ProviderCommercialPolicyService(listingRepository, unitRepository, access, policies);
    }

    private static PropertyListing listing(String providerId) {
        PropertyListing listing = PropertyListing.draft(LISTING_ID, TENANT, providerId,
                PropertyType.HOTEL, "Hotel");
        listing.updateDetails("Hotel", "Jl. Example", "Asia/Jakarta",
                LocalTime.of(14, 0), LocalTime.of(21, 0), LocalTime.of(11, 0));
        listing.publish();
        return listing;
    }

    private static AccommodationUnit unit() {
        return new AccommodationUnit(UNIT_ID, LISTING_ID, "Deluxe Room", 2, 5);
    }

    private static StayRatePlan plan() {
        return new StayRatePlan(UUID.randomUUID(), LISTING_ID, UNIT_ID, "BAR", "Best available",
                "IDR", new BigDecimal("500000"), FROM, UNTIL, true);
    }

    private static final class RecordingPolicies implements StayCommercialPolicyRepository {
        private StayRatePlan savedPlan;
        private Optional<StayRatePlan> plan = Optional.empty();
        private List<NightlyRateOverride> savedOverrides = List.of();
        private List<StayDateRestriction> savedRestrictions = List.of();

        @Override public Uni<StayRatePlan> saveRatePlan(String tenantId, StayRatePlan value) {
            savedPlan = value;
            plan = Optional.of(value);
            return Uni.createFrom().item(value);
        }
        @Override public Uni<Optional<StayRatePlan>> findRatePlan(String tenantId, UUID listingId,
                                                                  UUID unitId, UUID ratePlanId) {
            return Uni.createFrom().item(plan.filter(value -> value.id().equals(ratePlanId)));
        }
        @Override public Uni<List<StayRatePlan>> findRatePlans(String tenantId, UUID listingId, UUID unitId) {
            return Uni.createFrom().item(plan.stream().toList());
        }
        @Override public Uni<Void> saveNightlyRateOverrides(String tenantId, UUID listingId, UUID unitId,
                                                            UUID ratePlanId, List<NightlyRateOverride> values) {
            savedOverrides = List.copyOf(values);
            return Uni.createFrom().voidItem();
        }
        @Override public Uni<List<NightlyRateOverride>> findNightlyRateOverrides(String tenantId, UUID listingId,
                UUID unitId, UUID ratePlanId, LocalDate fromInclusive, LocalDate untilExclusive) {
            return Uni.createFrom().item(savedOverrides);
        }
        @Override public Uni<Void> saveStayDateRestrictions(String tenantId, UUID listingId, UUID unitId,
                                                            List<StayDateRestriction> values) {
            savedRestrictions = List.copyOf(values);
            return Uni.createFrom().voidItem();
        }
        @Override public Uni<List<StayDateRestriction>> findStayDateRestrictions(String tenantId, UUID listingId,
                UUID unitId, LocalDate fromInclusive, LocalDate untilExclusive) {
            return Uni.createFrom().item(savedRestrictions);
        }
    }
}
