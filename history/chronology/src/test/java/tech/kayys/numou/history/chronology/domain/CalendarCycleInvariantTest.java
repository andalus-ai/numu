package tech.kayys.numou.history.chronology.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalendarCycleInvariantTest {
    @Test
    void gregorian400YearCycleHas146097Days() {
        long start = GregorianCalendarDateConverter.toDayNumber(2000, 1, 1);
        long end = GregorianCalendarDateConverter.toDayNumber(2400, 1, 1);
        assertEquals(146097L, end - start);
    }

    @Test
    void julianFourYearCycleHas1461Days() {
        long start = JulianCalendarDateConverter.toDayNumber(1900, 1, 1);
        long end = JulianCalendarDateConverter.toDayNumber(1904, 1, 1);
        assertEquals(1461L, end - start);
    }

    @Test
    void consecutiveDaysRemainConsecutiveAcrossAstronomicalEraBoundary() {
        long lastBceDay = GregorianCalendarDateConverter.toDayNumber(0, 12, 31);
        long firstCeDay = GregorianCalendarDateConverter.toDayNumber(1, 1, 1);
        assertEquals(1L, firstCeDay - lastBceDay);
    }

    @Test
    void conversionRejectsAbsoluteDayOverflowRatherThanWrapping() {
        assertThrows(ArithmeticException.class,
                () -> GregorianCalendarDateConverter.toDayNumber(Long.MAX_VALUE, 1, 1));
    }
}
