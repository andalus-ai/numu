package tech.kayys.syirkah.restaurant.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingRules;
import tech.kayys.syirkah.restaurant.reservation.domain.ServicePeriod;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default booking policy from configuration ({@code syirkah.restaurant.booking.*}). Replace this producer with a
 * per-venue lookup when venues need their own rules.
 */
@ApplicationScoped
public class BookingRulesProducer {

    @ConfigMapping(prefix = "syirkah.restaurant.booking")
    public interface Config {
        @WithDefault("Asia/Jakarta") String zone();
        @WithDefault("1") int minParty();
        @WithDefault("12") int maxParty();
        @WithDefault("60") int maxAdvanceDays();
        @WithDefault("PT60M") Duration minLeadTime();
        @WithDefault("PT30M") Duration slotInterval();
        @WithDefault("PT15M") Duration cleanupBuffer();
        @WithDefault("8") int depositFromParty();
        @WithDefault("100000") long depositPerGuest();
        @WithDefault("IDR") String currency();
        @WithDefault("PT24H") Duration freeCancellationBefore();
        @WithDefault("50000") long lateFeePerGuest();
        @WithDefault("PT15M") Duration noShowGrace();
        @WithDefault("PT15M") Duration earlySeatingWindow();
        @WithDefault("11:30") LocalTime lunchOpens();
        @WithDefault("14:00") LocalTime lunchLastSeating();
        @WithDefault("17:00") LocalTime dinnerOpens();
        @WithDefault("21:00") LocalTime dinnerLastSeating();
    }

    @Inject
    Config c;

    @Produces
    @ApplicationScoped
    BookingRules rules() {
        final var periods = new EnumMap<DayOfWeek, List<ServicePeriod>>(DayOfWeek.class);
        for (final var d : DayOfWeek.values()) {
            periods.put(d, List.of(new ServicePeriod("lunch", c.lunchOpens(), c.lunchLastSeating()),
                    new ServicePeriod("dinner", c.dinnerOpens(), c.dinnerLastSeating())));
        }
        return new BookingRules(ZoneId.of(c.zone()), c.minParty(), c.maxParty(), c.maxAdvanceDays(), c.minLeadTime(), c.slotInterval(),
                c.cleanupBuffer(), Map.of(2, Duration.ofMinutes(75), 4, Duration.ofMinutes(90), 6, Duration.ofMinutes(105), 99, Duration.ofMinutes(120)),
                c.depositFromParty(), Money.of(c.depositPerGuest(), c.currency()), c.freeCancellationBefore(), Money.of(c.lateFeePerGuest(), c.currency()),
                c.noShowGrace(), c.earlySeatingWindow(), periods);
    }
}
