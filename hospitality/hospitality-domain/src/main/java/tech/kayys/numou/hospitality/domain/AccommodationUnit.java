package tech.kayys.numou.hospitality.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A unit/inventory class that can be mapped to the generic Reservation capability.
 * inventoryCount is a capacity descriptor, not a substitute for atomic reservation holds.
 */
public record AccommodationUnit(UUID id, UUID listingId, String name, int maxGuests, int inventoryCount) {
    public AccommodationUnit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listingId, "listingId");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        name = name.trim();
        if (maxGuests < 1) throw new IllegalArgumentException("maxGuests must be positive");
        if (inventoryCount < 1) throw new IllegalArgumentException("inventoryCount must be positive");
    }

    public boolean supportsGuests(int guestCount) {
        return guestCount >= 1 && guestCount <= maxGuests;
    }
}
