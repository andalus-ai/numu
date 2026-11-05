package tech.kayys.numou.history.chronology.domain;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class CalendarDateConverterTest {
    @Test
    void gregorianEpochAnchorsMatchKnownJulianDayNumber() {
        GregorianCalendarDateConverter converter = new GregorianCalendarDateConverter();
        assertEquals(OptionalLong.of(2451545L), converter.toAbsoluteDay(
                HistoricalDate.day(2000, 1, 1, GregorianCalendarDateConverter.ID)));
    }

    @Test
    void julianEpochAnchorsMatchKnownJulianDayNumber() {
        JulianCalendarDateConverter converter = new JulianCalendarDateConverter();
        assertEquals(OptionalLong.of(2451558L), converter.toAbsoluteDay(
                HistoricalDate.day(2000, 1, 1, JulianCalendarDateConverter.ID)));
    }

    @Test
    void gregorianAndJulianReformDatesNormalizeToSameAbsoluteDay() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalDate gregorian = HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID);
        HistoricalDate julian = HistoricalDate.day(1582, 10, 5, JulianCalendarDateConverter.ID);
        assertEquals(TemporalRelation.SAME, chronology.compare(gregorian, julian).orElseThrow());
    }

    @Test
    void absoluteDayCanBeConvertedBackIntoEitherCalendar() {
        GregorianCalendarDateConverter gregorian = new GregorianCalendarDateConverter();
        JulianCalendarDateConverter julian = new JulianCalendarDateConverter();
        long absoluteDay = gregorian.toAbsoluteDay(
                HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID)).orElseThrow();
        assertEquals(HistoricalDate.day(1582, 10, 5, JulianCalendarDateConverter.ID),
                julian.fromAbsoluteDay(absoluteDay));
        assertEquals(HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID),
                gregorian.fromAbsoluteDay(absoluteDay));
    }

    @Test
    void chronologyConvertsExactDatesButDoesNotInventDatesForPartialPrecision() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalDate gregorian = HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID);
        assertEquals(HistoricalDate.day(1582, 10, 5, JulianCalendarDateConverter.ID),
                chronology.convert(gregorian, JulianCalendarDateConverter.ID).orElseThrow());
        assertTrue(chronology.convert(
                HistoricalDate.year(1582, GregorianCalendarDateConverter.ID),
                JulianCalendarDateConverter.ID).isEmpty());
    }

    @Test
    void julianLeapYearRulesDifferFromGregorianAtCenturyBoundary() {
        JulianCalendarDateConverter julian = new JulianCalendarDateConverter();
        GregorianCalendarDateConverter gregorian = new GregorianCalendarDateConverter();
        assertTrue(julian.toAbsoluteDay(HistoricalDate.day(1900, 2, 29, JulianCalendarDateConverter.ID)).isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> gregorian.toAbsoluteDay(HistoricalDate.day(1900, 2, 29, GregorianCalendarDateConverter.ID)));
    }

    @Test
    void rejectsImpossibleMonthDays() {
        GregorianCalendarDateConverter converter = new GregorianCalendarDateConverter();
        assertThrows(IllegalArgumentException.class,
                () -> converter.toAbsoluteDay(HistoricalDate.day(2023, 2, 29, GregorianCalendarDateConverter.ID)));
    }

    @Test
    void partialDatesProduceCalendarAwareInclusiveBounds() {
        GregorianCalendarDateConverter converter = new GregorianCalendarDateConverter();
        AbsoluteDayRange february = converter.toAbsoluteDayBounds(
                HistoricalDate.month(2024, 2, GregorianCalendarDateConverter.ID)).orElseThrow();
        assertEquals(29, february.lastDay() - february.firstDay() + 1);
        assertTrue(converter.toAbsoluteDay(HistoricalDate.year(2024, GregorianCalendarDateConverter.ID)).isEmpty());
    }

    @Test
    void astronomicalBceDatesNormalizeInChronologicalOrder() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalDate bce = HistoricalDate.day(0, 1, 1, GregorianCalendarDateConverter.ID);
        HistoricalDate ce = HistoricalDate.day(1, 1, 1, GregorianCalendarDateConverter.ID);
        assertEquals(TemporalRelation.BEFORE, chronology.compare(bce, ce).orElseThrow());
    }

    @Test
    void partialDateComparisonsReturnOverlapRatherThanInventingAnOrder() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalDate year = HistoricalDate.year(2000, GregorianCalendarDateConverter.ID);
        HistoricalDate june = HistoricalDate.month(2000, 6, GregorianCalendarDateConverter.ID);
        assertEquals(TemporalRelation.OVERLAPS, chronology.compare(year, june).orElseThrow());
    }

    @Test
    void unknownCalendarReturnsNoComparison() {
        HistoricalChronology chronology = HistoricalChronology.standard();
        HistoricalDate unknown = HistoricalDate.year(1200, CalendarSystemId.of("hijri"));
        HistoricalDate known = HistoricalDate.year(1200, GregorianCalendarDateConverter.ID);
        assertTrue(chronology.compare(unknown, known).isEmpty());
    }
}
