package tech.kayys.numou.hospitality.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StayRestrictionPolicyTest {
    private static final UUID LISTING = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-12-10");
    private static final LocalDate CHECK_OUT = LocalDate.parse("2026-12-13");

    @Test
    void acceptsStayWhenNoApplicableRestrictionExists() {
        StayDateRestriction otherUnit = restriction(UUID.randomUUID(), CHECK_IN, 1, 0,
                false, false, true);
        assertThatCode(() -> StayRestrictionPolicy.validate(LISTING, UNIT, CHECK_IN, CHECK_OUT,
                List.of(otherUnit))).doesNotThrowAnyException();
    }

    @Test
    void rejectsClosedArrivalAndDeparture() {
        assertViolation(restriction(UNIT, CHECK_IN, 1, 0, true, false, false), "CLOSED_TO_ARRIVAL");
        assertViolation(restriction(UNIT, CHECK_OUT, 1, 0, false, true, false), "CLOSED_TO_DEPARTURE");
    }

    @Test
    void rejectsStopSellOnAnyOccupiedNightButNotCheckoutDate() {
        assertViolation(restriction(UNIT, CHECK_IN.plusDays(1), 1, 0,
                false, false, true), "STOP_SELL");
        assertThatCode(() -> StayRestrictionPolicy.validate(LISTING, UNIT, CHECK_IN, CHECK_OUT,
                List.of(restriction(UNIT, CHECK_OUT, 1, 0, false, false, true))))
                .doesNotThrowAnyException();
    }

    @Test
    void enforcesMinimumAndMaximumStayOnArrivalDate() {
        assertViolation(restriction(UNIT, CHECK_IN, 4, 0, false, false, false), "MINIMUM_STAY");
        assertViolation(restriction(UNIT, CHECK_IN, 1, 2, false, false, false), "MAXIMUM_STAY");
    }

    @Test
    void rejectsInvalidRestrictionBounds() {
        assertThatThrownBy(() -> restriction(UNIT, CHECK_IN, 3, 2, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertViolation(StayDateRestriction restriction, String code) {
        assertThatThrownBy(() -> StayRestrictionPolicy.validate(LISTING, UNIT, CHECK_IN, CHECK_OUT,
                List.of(restriction)))
                .isInstanceOf(StayRestrictionViolationException.class)
                .hasMessageContaining(code);
    }

    private static StayDateRestriction restriction(UUID unit, LocalDate date, int min, int max,
                                                   boolean cta, boolean ctd, boolean stopSell) {
        return new StayDateRestriction(LISTING, unit, date, min, max, cta, ctd, stopSell);
    }
}
