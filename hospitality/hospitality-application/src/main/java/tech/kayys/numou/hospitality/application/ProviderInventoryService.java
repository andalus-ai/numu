package tech.kayys.numou.hospitality.application;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.AccommodationUnit;
import tech.kayys.numou.hospitality.domain.PropertyListing;
import tech.kayys.numou.hospitality.spi.AccommodationUnitRepository;
import tech.kayys.numou.hospitality.spi.PropertyListingRepository;
import tech.kayys.numou.hospitality.spi.ProviderAccessPolicy;
import tech.kayys.numou.hospitality.spi.StayInventoryProvisioningPort;

/** Provider use case for configuring sellable capacity across a stay-date range. */
public final class ProviderInventoryService {
    private final PropertyListingRepository listings;
    private final AccommodationUnitRepository units;
    private final ProviderAccessPolicy access;
    private final StayInventoryProvisioningPort inventory;

    public ProviderInventoryService(PropertyListingRepository listings, AccommodationUnitRepository units,
                                    ProviderAccessPolicy access, StayInventoryProvisioningPort inventory) {
        this.listings = Objects.requireNonNull(listings, "listings");
        this.units = Objects.requireNonNull(units, "units");
        this.access = Objects.requireNonNull(access, "access");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public record ProvisionCapacity(String tenantId, String actorId, String providerId,
                                   UUID listingId, UUID unitId, LocalDate checkIn,
                                   LocalDate checkOut, int capacity) { }

    public Uni<Void> provision(ProvisionCapacity command) {
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
        if (command.capacity() < 1) throw new IllegalArgumentException("capacity must be positive");

        return access.requireCanManageListings(command.tenantId(), command.actorId(), command.providerId())
                .flatMap(ignored -> listings.findById(command.tenantId(), command.listingId()))
                .flatMap(found -> {
                    PropertyListing listing = found.orElseThrow(ListingNotFoundException::new);
                    if (!listing.providerId().equals(command.providerId())) {
                        return Uni.createFrom().failure(new ListingNotFoundException());
                    }
                    return units.findById(command.tenantId(), command.listingId(), command.unitId())
                            .flatMap(unit -> {
                                AccommodationUnit accommodationUnit = unit.orElseThrow(ListingNotFoundException::new);
                                return inventory.provision(command.tenantId(), listing.id(), accommodationUnit.id(),
                                        command.checkIn(), command.checkOut(), command.capacity());
                            });
                });
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
