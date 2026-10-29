package tech.kayys.numou.hospitality.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Evaluates date-specific restrictions for an exclusive-checkout stay interval. */
public final class StayRestrictionPolicy {
    private StayRestrictionPolicy() { }

    public static void validate(UUID listingId, UUID unitId, LocalDate checkIn, LocalDate checkOut,
                                List<StayDateRestriction> restrictions) {
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(checkIn, "checkIn");
        Objects.requireNonNull(checkOut, "checkOut");
        Objects.requireNonNull(restrictions, "restrictions");
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        for (StayDateRestriction restriction : List.copyOf(restrictions)) {
            if (!listingId.equals(restriction.listingId()) || !unitId.equals(restriction.unitId())) {
                continue;
            }
            if (restriction.date().equals(checkIn)) {
                if (restriction.closedToArrival()) {
                    throw new StayRestrictionViolationException("CLOSED_TO_ARRIVAL");
                }
                if (nights < restriction.minStayNights()) {
                    throw new StayRestrictionViolationException("MINIMUM_STAY");
                }
                if (restriction.maxStayNights() > 0 && nights > restriction.maxStayNights()) {
                    throw new StayRestrictionViolationException("MAXIMUM_STAY");
                }
            }
            if (restriction.date().equals(checkOut) && restriction.closedToDeparture()) {
                throw new StayRestrictionViolationException("CLOSED_TO_DEPARTURE");
            }
            if (!restriction.date().isBefore(checkIn) && restriction.date().isBefore(checkOut)
                    && restriction.stopSell()) {
                throw new StayRestrictionViolationException("STOP_SELL");
            }
        }
    }
}
