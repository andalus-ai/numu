package tech.kayys.numou.hospitality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StayRatePlanTest {
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID LISTING = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.parse("2026-11-01");
    private static final LocalDate UNTIL = LocalDate.parse("2027-01-01");

    @Test
    void resolvesBaseRateAndDateSpecificOverride() {
        StayRatePlan plan = plan(true);
        assertThat(plan.nightlyRate(LocalDate.parse("2026-12-01"), Optional.empty()))
                .isEqualByComparingTo("500000");
        assertThat(plan.nightlyRate(LocalDate.parse("2026-12-02"),
                Optional.of(new NightlyRateOverride(PLAN, LocalDate.parse("2026-12-02"),
                        new BigDecimal("650000")))))
                .isEqualByComparingTo("650000");
    }

    @Test
    void rejectsDisabledPlanOutOfRangeAndMismatchedOverride() {
        assertThatThrownBy(() -> plan(false).nightlyRate(LocalDate.parse("2026-12-01"), Optional.empty()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> plan(true).nightlyRate(UNTIL, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan(true).nightlyRate(LocalDate.parse("2026-12-02"),
                Optional.of(new NightlyRateOverride(UUID.randomUUID(), LocalDate.parse("2026-12-02"),
                        new BigDecimal("650000")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesPlanRangeAndMoney() {
        assertThatThrownBy(() -> new StayRatePlan(PLAN, LISTING, UNIT, "BAR", "Best available",
                "IDR", BigDecimal.ONE, UNTIL, FROM, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StayRatePlan(PLAN, LISTING, UNIT, "BAR", "Best available",
                "IDR", new BigDecimal("-1"), FROM, UNTIL, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StayRatePlan plan(boolean enabled) {
        return new StayRatePlan(PLAN, LISTING, UNIT, "BAR", "Best available", "IDR",
                new BigDecimal("500000"), FROM, UNTIL, enabled);
    }
}
