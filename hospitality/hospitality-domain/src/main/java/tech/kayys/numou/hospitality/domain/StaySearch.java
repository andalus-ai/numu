package tech.kayys.numou.hospitality.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Customer request for a date-based stay. Check-out is exclusive. */
public record StaySearch(UUID listingId, UUID unitId, LocalDate checkIn, LocalDate checkOut, int guestCount) {
    public StaySearch {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        if (guestCount < 1) {
            throw new IllegalArgumentException("guestCount must be positive");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }
}
