package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.ListingStatus;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayInventoryProvisioningPort;

/**
 * Provider-side inventory provisioning orchestration.
 *
 * <p>Authorization is evaluated before resolving provider-owned resources. The persistence
 * port remains responsible for atomic date-row writes and for protecting active bookings
 * when capacity is changed.</p>
 */
public final class ProviderStayInventoryService {
    private final PropertyListingRepository listings;
    private final AccommodationUnitRepository units;
    private final ProviderAccessPolicy access;
    private final StayInventoryProvisioningPort inventory;

    public ProviderStayInventoryService(PropertyListingRepository listings,
                                        AccommodationUnitRepository units,
                                        ProviderAccessPolicy access,
                                        StayInventoryProvisioningPort inventory) {
        this.listings = Objects.requireNonNull(listings, "listings");
        this.units = Objects.requireNonNull(units, "units");
        this.access = Objects.requireNonNull(access, "access");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public record ProvisionInventory(String tenantId, String actorId, String providerId,
                                     UUID listingId, UUID unitId,
                                     LocalDate checkIn, LocalDate checkOut, int capacity) { }

    /**
     * Provisions sellable capacity for every night in [checkIn, checkOut).
     * Capacity may be lower than the physical unit count (for maintenance/operational
     * closures), but must never exceed it.
     */
    public Uni<Void> provision(ProvisionInventory command) {
        Objects.requireNonNull(command, "command");
        requireText(command.tenantId(), "tenantId");
        requireText(command.actorId(), "actorId");
        requireText(command.providerId(), "providerId");
        Objects.requireNonNull(command.listingId(), "listingId");
        Objects.requireNonNull(command.unitId(), "unitId");
        Objects.requireNonNull(command.checkIn(), "checkIn");
        Objects.requireNonNull(command.checkOut(), "checkOut");
        if (!command.checkOut().isAfter(command.checkIn())) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        if (command.capacity() < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        return access.requireCanManageListings(command.tenantId(), command.actorId(), command.providerId())
                .flatMap(ignored -> listings.findById(command.tenantId(), command.listingId()))
                .flatMap(found -> {
                    if (found.isEmpty()) {
                        return Uni.createFrom().failure(new ListingNotFoundException());
                    }
                    PropertyListing listing = found.get();
                    if (!command.tenantId().equals(listing.tenantId())
                            || !command.providerId().equals(listing.providerId())
                            || listing.status() == ListingStatus.ARCHIVED) {
                        // Do not disclose whether another provider/tenant owns the identifier.
                        return Uni.createFrom().failure(new ListingNotFoundException());
                    }
                    return units.findById(command.tenantId(), listing.id(), command.unitId())
                            .flatMap(foundUnit -> {
                                if (foundUnit.isEmpty()) {
                                    return Uni.createFrom().failure(new ListingNotFoundException());
                                }
                                AccommodationUnit unit = foundUnit.get();
                                if (!unit.listingId().equals(listing.id())
                                        || command.capacity() > unit.inventoryCount()) {
                                    return Uni.createFrom().failure(new IllegalArgumentException(
                                            "capacity must not exceed the accommodation unit's physical inventory"));
                                }
                                return inventory.provision(command.tenantId(), listing.id(), unit.id(),
                                        command.checkIn(), command.checkOut(), command.capacity());
                            });
                });
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
