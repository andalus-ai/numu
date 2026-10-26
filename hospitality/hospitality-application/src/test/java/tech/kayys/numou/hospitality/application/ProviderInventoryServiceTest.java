package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayInventoryProvisioningPort;

class ProviderInventoryServiceTest {
    private final UUID listingId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @Test
    void provisionsCapacityOnlyAfterProviderAccessAndOwnershipChecks() {
        PropertyListing listing = PropertyListing.draft(listingId, "tenant-a", "provider-a",
                PropertyType.HOTEL, "Test hotel");
        AccommodationUnit unit = new AccommodationUnit(unitId, listingId, "Standard", 2, 3);
        AtomicBoolean provisioned = new AtomicBoolean();
        ProviderInventoryService service = new ProviderInventoryService(
                listings(listing), units(unit),
                (tenant, actor, provider) -> Uni.createFrom().voidItem(),
                (tenant, listingKey, unitKey, from, to, capacity) -> {
                    assertThat(tenant).isEqualTo("tenant-a");
                    assertThat(listingKey).isEqualTo(listingId);
                    assertThat(unitKey).isEqualTo(unitId);
                    assertThat(from).isEqualTo(LocalDate.parse("2026-11-01"));
                    assertThat(to).isEqualTo(LocalDate.parse("2026-11-04"));
                    assertThat(capacity).isEqualTo(2);
                    provisioned.set(true);
                    return Uni.createFrom().voidItem();
                });

        service.provision(command("provider-a")).await().indefinitely();

        assertThat(provisioned).isTrue();
    }

    @Test
    void doesNotProvisionForAnotherProvidersListing() {
        PropertyListing listing = PropertyListing.draft(listingId, "tenant-a", "provider-owner",
                PropertyType.HOTEL, "Test hotel");
        AtomicBoolean provisioned = new AtomicBoolean();
        ProviderInventoryService service = new ProviderInventoryService(
                listings(listing), units(new AccommodationUnit(unitId, listingId, "Standard", 2, 3)),
                (tenant, actor, provider) -> Uni.createFrom().voidItem(),
                (tenant, listingKey, unitKey, from, to, capacity) -> {
                    provisioned.set(true);
                    return Uni.createFrom().voidItem();
                });

        assertThatThrownBy(() -> service.provision(command("provider-attacker")).await().indefinitely())
                .hasRootCauseInstanceOf(ProviderInventoryService.class)
                .or().isInstanceOf(ListingNotFoundException.class);
        assertThat(provisioned).isFalse();
    }

    private ProviderInventoryService.ProvisionCapacity command(String provider) {
        return new ProviderInventoryService.ProvisionCapacity("tenant-a", "actor-a", provider, listingId, unitId,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-04"), 2);
    }

    private static PropertyListingRepository listings(PropertyListing listing) {
        return new PropertyListingRepository() {
            public Uni<PropertyListing> save(String tenantId, PropertyListing value) { return Uni.createFrom().item(value); }
            public Uni<Optional<PropertyListing>> findById(String tenantId, UUID id) {
                return Uni.createFrom().item(tenantId.equals(listing.tenantId()) && id.equals(listing.id())
                        ? Optional.of(listing) : Optional.empty());
            }
            public Uni<List<PropertyListing>> findByProvider(String tenantId, String providerId) { return Uni.createFrom().item(List.of()); }
            public Uni<List<PropertyListing>> findPublished(String tenantId) { return Uni.createFrom().item(List.of()); }
            public Uni<Boolean> existsById(String tenantId, UUID id) { return Uni.createFrom().item(false); }
        };
    }

    private static AccommodationUnitRepository units(AccommodationUnit unit) {
        return (tenantId, listingId, unitId) -> Uni.createFrom().item(
                listingId.equals(unit.listingId()) && unitId.equals(unit.id())
                        ? Optional.of(unit) : Optional.empty());
    }
}
