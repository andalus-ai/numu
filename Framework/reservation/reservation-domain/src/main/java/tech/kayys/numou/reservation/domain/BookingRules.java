package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facility booking configuration and constraints.
 */
public record BookingRules(
        ZoneId zone,
        int minParty,
        int maxParty,
        int maxAdvanceDays,
        Duration minLeadTime,
        Duration defaultTurnTime,
        Duration cleanupBuffer,
        Map<Integer, Duration> turnTimeByPartySize,
        int depositThreshold,
        Money depositPerPerson,
        Duration freeCancellationBefore,
        Money lateCancellationFee,
        Duration noShowGrace,
        Duration earlyArrivalWindow,
        Map<DayOfWeek, List<OperatingWindow>> operatingSchedule
) {
    public BookingRules {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(minLeadTime, "minLeadTime cannot be null");
        Objects.requireNonNull(defaultTurnTime, "defaultTurnTime cannot be null");
        Objects.requireNonNull(cleanupBuffer, "cleanupBuffer cannot be null");
        turnTimeByPartySize = turnTimeByPartySize == null ? Map.of() : Map.copyOf(turnTimeByPartySize);
        Objects.requireNonNull(depositPerPerson, "depositPerPerson cannot be null");
        Objects.requireNonNull(freeCancellationBefore, "freeCancellationBefore cannot be null");
        Objects.requireNonNull(lateCancellationFee, "lateCancellationFee cannot be null");
        Objects.requireNonNull(noShowGrace, "noShowGrace cannot be null");
        earlyArrivalWindow = earlyArrivalWindow == null ? Duration.ofMinutes(15) : earlyArrivalWindow;
        operatingSchedule = operatingSchedule == null ? Map.of() : Map.copyOf(operatingSchedule);
    }

    public static BookingRules of(
            ZoneId zone, int minParty, int maxParty, int maxAdvanceDays, Duration minLeadTime, Duration defaultTurnTime,
            Duration cleanupBuffer, Map<Integer, Duration> turnTimeByPartySize, int depositThreshold, Money depositPerPerson,
            Duration freeCancellationBefore, Money lateCancellationFee, Duration noShowGrace, Duration earlyArrivalWindow,
            Map<DayOfWeek, ?> rawSchedule
    ) {
        return new BookingRules(zone, minParty, maxParty, maxAdvanceDays, minLeadTime, defaultTurnTime, cleanupBuffer, turnTimeByPartySize,
                depositThreshold, depositPerPerson, freeCancellationBefore, lateCancellationFee, noShowGrace, earlyArrivalWindow,
                convertSchedule(rawSchedule));
    }

    private static Map<DayOfWeek, List<OperatingWindow>> convertSchedule(Map<DayOfWeek, ?> raw) {
        if (raw == null) return Map.of();
        final var res = new java.util.EnumMap<DayOfWeek, List<OperatingWindow>>(DayOfWeek.class);
        for (var entry : raw.entrySet()) {
            if (entry.getValue() instanceof List<?> list) {
                final var windows = new java.util.ArrayList<OperatingWindow>();
                for (Object item : list) {
                    if (item instanceof OperatingWindow ow) windows.add(ow);
                    else if (item instanceof ServicePeriod sp) windows.add(sp.toOperatingWindow());
                }
                res.put(entry.getKey(), List.copyOf(windows));
            }
        }
        return Map.copyOf(res);
    }

    public Duration turnTimeFor(PartySize party) {
        return turnTimeByPartySize.entrySet().stream()
                .filter(e -> party.value() <= e.getKey())
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(defaultTurnTime);
    }

    public boolean requiresDeposit(PartySize party) {
        return depositThreshold > 0 && party.value() >= depositThreshold && depositPerPerson.isPositive();
    }

    public Money depositFor(PartySize party) {
        return requiresDeposit(party)
                ? depositPerPerson.multiply(java.math.BigDecimal.valueOf(party.value()))
                : Money.zero(depositPerPerson.currency());
    }

    public Money lateFeeFor(PartySize party) {
        return lateCancellationFee;
    }

    public List<OperatingWindow> periodsOn(DayOfWeek day) {
        return operatingSchedule.getOrDefault(day, List.of());
    }

    // Compatibility method for restaurant contexts
    public Duration earlySeatingWindow() {
        return earlyArrivalWindow;
    }

    public Duration slotInterval() {
        return Duration.ofMinutes(15);
    }
}
