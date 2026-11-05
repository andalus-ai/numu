package tech.kayys.numou.history.chronology.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalDateRangeTest {
    private static final CalendarSystemId GREGORIAN = CalendarSystemId.of("gregorian");

    @Test
    void acceptsOrderedClosedRangeAcrossBceAndCeBoundary() {
        HistoricalDateRange range = new HistoricalDateRange(
                HistoricalDate.year(0, GREGORIAN),
                HistoricalDate.year(1, GREGORIAN));
        assertEquals(GREGORIAN, range.calendarSystem());
        assertFalse(range.isSingleNominalDate());
    }

    @Test
    void rejectsReversedRange() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDateRange(
                HistoricalDate.year(10, GREGORIAN),
                HistoricalDate.year(9, GREGORIAN)));
    }

    @Test
    void rejectsDifferentCalendars() {
        assertThrows(IllegalArgumentException.class, () -> new HistoricalDateRange(
                HistoricalDate.year(100, GREGORIAN),
                HistoricalDate.year(101, CalendarSystemId.of("julian"))));
    }
}
