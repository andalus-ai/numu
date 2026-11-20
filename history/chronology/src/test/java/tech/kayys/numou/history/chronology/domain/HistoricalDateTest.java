package tech.kayys.numou.history.chronology.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalDateTest {
    private static final CalendarSystemId GREGORIAN = CalendarSystemId.of("gregorian");

    @Test
    void astronomicalYearZeroIsDisplayedAsOneBce() {
        HistoricalDate date = HistoricalDate.year(0, GREGORIAN);
        assertEquals(1, date.displayedYearOfEra());
        assertEquals("BCE", date.eraLabel());
    }

    @Test
    void negativeAstronomicalYearsConvertWithoutUserFacingYearZero() {
        HistoricalDate date = HistoricalDate.year(-43, GREGORIAN);
        assertEquals(44, date.displayedYearOfEra());
        assertEquals("BCE", date.eraLabel());
    }

    @Test
    void positiveYearsUseCe() {
        HistoricalDate date = HistoricalDate.year(2026, GREGORIAN);
        assertEquals(2026, date.displayedYearOfEra());
        assertEquals("CE", date.eraLabel());
    }

    @Test
    void partialPrecisionMustNotCarryInventedFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new HistoricalDate(1200, 4, 2, DatePrecision.YEAR, GREGORIAN, false, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoricalDate(1200, 4, 1, DatePrecision.MONTH, GREGORIAN, false, ""));
    }

    @Test
    void approximateDateRetainsOriginalExpression() {
        HistoricalDate date = HistoricalDate.year(1200, GREGORIAN).asApproximate("c. 1200 CE");
        assertTrue(date.approximate());
        assertEquals("c. 1200 CE", date.sourceExpression().orElseThrow());
    }

    @Test
    void nominalComparisonRejectsMixedCalendars() {
        HistoricalDate gregorian = HistoricalDate.day(1582, 10, 15, GREGORIAN);
        HistoricalDate julian = HistoricalDate.day(1582, 10, 5, CalendarSystemId.of("julian"));
        assertThrows(IllegalArgumentException.class, () -> gregorian.compareNominallyTo(julian));
    }

    @Test
    void comparesWithinSameCalendarUsingAstronomicalYearOrder() {
        HistoricalDate earlier = HistoricalDate.year(-1, GREGORIAN);
        HistoricalDate later = HistoricalDate.year(0, GREGORIAN);
        assertTrue(earlier.compareNominallyTo(later) < 0);
    }
}
