package tech.kayys.numou.hospitality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.domain.PropertyType;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayInventoryProvisioningPort;

class ProviderStayInventoryServiceTest {
    private static final String TENANT = "tenant-a";
    private static final String PROVIDER = "provider-a";
    private static final String ACTOR = "actor-a";
    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 12, 13);

    @Test
    void provisionsAuthorizedCapacityForExclusiveDateRange() {
        PropertyListing listing = listing(PROVIDER);
        AccommodationUnit unit = new AccommodationUnit(UNIT_ID, LISTING_ID, "Deluxe Room", 3, 8);
        RecordingInventory inventory = new RecordingInventory();
        ProviderStayInventoryService service = service(listing, unit, inventory, (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        service.provision(command(5, CHECK_IN, CHECK_OUT)).await().indefinitely();

        assertThat(inventory.calls).hasSize(1);
        assertThat(inventory.calls.get(0).tenantId()).isEqualTo(TENANT);
        assertThat(inventory.calls.get(0).listingId()).isEqualTo(LISTING_ID);
        assertThat(inventory.calls.get(0).unitId()).isEqualTo(UNIT_ID);
        assertThat(inventory.calls.get(0).checkIn()).isEqualTo(CHECK_IN);
        assertThat(inventory.calls.get(0).checkOut()).isEqualTo(CHECK_OUT);
        assertThat(inventory.calls.get(0).capacity()).isEqualTo(5);
    }

    @Test
    void rejectsCapacityAbovePhysicalInventoryBeforeProvisioning() {
        RecordingInventory inventory = new RecordingInventory();
        ProviderStayInventoryService service = service(listing(PROVIDER),
                new AccommodationUnit(UNIT_ID, LISTING_ID, "Room", 2, 4),
                inventory, (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        assertThatThrownBy(() -> service.provision(command(5, CHECK_IN, CHECK_OUT))
                .await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inventory.calls).isEmpty();
    }

    @Test
    void doesNotProvisionForAnotherProvidersListing() {
        RecordingInventory inventory = new RecordingInventory();
        ProviderStayInventoryService service = service(listing("provider-b"),
                new AccommodationUnit(UNIT_ID, LISTING_ID, "Room", 2, 4),
                inventory, (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        assertThatThrownBy(() -> service.provision(command(2, CHECK_IN, CHECK_OUT))
                .await().indefinitely())
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(inventory.calls).isEmpty();
    }

    @Test
    void doesNotProvisionArchivedListing() {
        PropertyListing listing = listing(PROVIDER);
        listing.archive();
        RecordingInventory inventory = new RecordingInventory();
        ProviderStayInventoryService service = service(listing,
                new AccommodationUnit(UNIT_ID, LISTING_ID, "Room", 2, 4),
                inventory, (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        assertThatThrownBy(() -> service.provision(command(2, CHECK_IN, CHECK_OUT))
                .await().indefinitely())
                .isInstanceOf(ListingNotFoundException.class);
        assertThat(inventory.calls).isEmpty();
    }

    @Test
    void rejectsInvalidDateRangeAndCapacity() {
        ProviderStayInventoryService service = service(listing(PROVIDER),
                new AccommodationUnit(UNIT_ID, LISTING_ID, "Room", 2, 4),
                new RecordingInventory(), (tenantId, actorId, providerId) -> Uni.createFrom().voidItem());

        assertThatThrownBy(() -> service.provision(command(1, CHECK_IN, CHECK_IN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.provision(command(0, CHECK_IN, CHECK_OUT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authorizationFailurePreventsResourceLookupAndProvisioning() {
        RecordingInventory inventory = new RecordingInventory();
        ProviderStayInventoryService service = service(listing(PROVIDER),
                new AccommodationUnit(UNIT_ID, LISTING_ID, "Room", 2, 4),
                inventory, (tenantId, actorId, providerId) -> Uni.createFrom().failure(new SecurityException("denied")));

        assertThatThrownBy(() -> service.provision(command(2, CHECK_IN, CHECK_OUT))
                .await().indefinitely())
                .isInstanceOf(SecurityException.class);
        assertThat(inventory.calls).isEmpty();
    }

    private static ProviderStayInventoryService service(PropertyListing listing, AccommodationUnit unit,
                                                         RecordingInventory inventory,
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
        return new ProviderStayInventoryService(listingRepository, unitRepository, access, inventory);
    }

    private static PropertyListing listing(String providerId) {
        PropertyListing listing = PropertyListing.draft(LISTING_ID, TENANT, providerId,
                PropertyType.HOTEL, "Hotel");
        listing.updateDetails("Hotel", "Jl. Example", "Asia/Jakarta",
                LocalTime.of(14, 0), LocalTime.of(21, 0), LocalTime.of(11, 0));
        listing.publish();
        return listing;
    }

    private static ProviderStayInventoryService.ProvisionInventory command(
            int capacity, LocalDate checkIn, LocalDate checkOut) {
        return new ProviderStayInventoryService.ProvisionInventory(
                TENANT, ACTOR, PROVIDER, LISTING_ID, UNIT_ID, checkIn, checkOut, capacity);
    }

    private static final class RecordingInventory implements StayInventoryProvisioningPort {
        private final java.util.ArrayList<ProvisionCall> calls = new java.util.ArrayList<>();

        @Override
        public Uni<Void> provision(String tenantId, UUID listingId, UUID unitId,
                                   LocalDate checkIn, LocalDate checkOut, int capacity) {
            calls.add(new ProvisionCall(tenantId, listingId, unitId, checkIn, checkOut, capacity));
            return Uni.createFrom().voidItem();
        }
    }

    private record ProvisionCall(String tenantId, UUID listingId, UUID unitId,
                                 LocalDate checkIn, LocalDate checkOut, int capacity) { }
}
