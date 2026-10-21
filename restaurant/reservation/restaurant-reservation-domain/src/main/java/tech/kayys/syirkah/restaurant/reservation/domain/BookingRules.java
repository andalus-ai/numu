package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A venue's reservation policy: who may book, when, for how long, and what no-shows cost.
 *
 * @param turnTimes       upper-bound party size -> dining duration, e.g. {2: 75m, 4: 90m, 6: 105m, 99: 120m}
 * @param depositFromParty deposit is required when the party is at least this large (0 disables deposits)
 * @param depositPerGuest  deposit amount per guest
 * @param lateFeePerGuest  charged for late cancellation / no-show when no deposit was taken (may be zero)
 */
public record BookingRules(
        ZoneId zone,
        int minParty,
        int maxParty,
        int maxAdvanceDays,
        Duration minLeadTime,
        Duration slotInterval,
        Duration cleanupBuffer,
        Map<Integer, Duration> turnTimes,
        int depositFromParty,
        Money depositPerGuest,
        Duration freeCancellationBefore,
        Money lateFeePerGuest,
        Duration noShowGrace,
        Duration earlySeatingWindow,
        Map<DayOfWeek, List<ServicePeriod>> servicePeriods) {

    public BookingRules {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(minLeadTime, "minLeadTime cannot be null");
        Objects.requireNonNull(slotInterval, "slotInterval cannot be null");
        Objects.requireNonNull(cleanupBuffer, "cleanupBuffer cannot be null");
        Objects.requireNonNull(depositPerGuest, "depositPerGuest cannot be null");
        Objects.requireNonNull(freeCancellationBefore, "freeCancellationBefore cannot be null");
        Objects.requireNonNull(lateFeePerGuest, "lateFeePerGuest cannot be null");
        Objects.requireNonNull(noShowGrace, "noShowGrace cannot be null");
        Objects.requireNonNull(earlySeatingWindow, "earlySeatingWindow cannot be null");
        if (minParty < 1 || maxParty < minParty) {
            throw new IllegalArgumentException("Party limits must satisfy 1 <= min <= max");
        }
        if (slotInterval.isZero() || slotInterval.isNegative()) {
            throw new IllegalArgumentException("slotInterval must be positive");
        }
        if (turnTimes == null || turnTimes.isEmpty()) {
            throw new IllegalArgumentException("turnTimes cannot be empty");
        }
        turnTimes = Map.copyOf(new TreeMap<>(turnTimes));
        servicePeriods = servicePeriods == null ? Map.of() : Map.copyOf(servicePeriods);
    }

    /** Dining duration for a party: the entry with the smallest bound that still covers the party. */
    public Duration turnTimeFor(PartySize party) {
        return new TreeMap<>(turnTimes).ceilingEntry(party.value()) != null
                ? new TreeMap<>(turnTimes).ceilingEntry(party.value()).getValue()
                : new TreeMap<>(turnTimes).lastEntry().getValue();
    }

    public boolean requiresDeposit(PartySize party) {
        return depositFromParty > 0 && party.value() >= depositFromParty;
    }

    public Money depositFor(PartySize party) {
        return depositPerGuest.multiply(java.math.BigDecimal.valueOf(party.value()));
    }

    public Money lateFeeFor(PartySize party) {
        return lateFeePerGuest.multiply(java.math.BigDecimal.valueOf(party.value()));
    }

    public List<ServicePeriod> periodsOn(DayOfWeek day) {
        return servicePeriods.getOrDefault(day, List.of());
    }
}
