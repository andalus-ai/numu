package tech.kayys.numou.hospitality.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Date-specific sell restrictions for one accommodation unit.
 * maxStayNights=0 means no maximum. A restriction applies to the local property
 * date supplied by the caller; timezone conversion belongs at the API boundary.
 */
public record StayDateRestriction(UUID listingId, UUID unitId, LocalDate date,
                                  int minStayNights, int maxStayNights,
                                  boolean closedToArrival, boolean closedToDeparture,
                                  boolean stopSell) {
    public StayDateRestriction {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(date, "date");
        if (minStayNights < 1) throw new IllegalArgumentException("minStayNights must be positive");
        if (maxStayNights < 0) throw new IllegalArgumentException("maxStayNights must not be negative");
        if (maxStayNights > 0 && maxStayNights < minStayNights) {
            throw new IllegalArgumentException("maxStayNights must be zero or at least minStayNights");
        }
    }
}
