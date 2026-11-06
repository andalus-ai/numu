package tech.kayys.numou.history.chronology.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalYearParserTest {
    private static final CalendarSystemId GREGORIAN = CalendarSystemId.of("gregorian");

    @Test
    void parsesBceAndCeWithoutAstronomicalYearZeroConfusion() {
        assertEquals(0, HistoricalYearParser.parse("1 BCE", GREGORIAN).astronomicalYear());
        assertEquals(-43, HistoricalYearParser.parse("44 BC", GREGORIAN).astronomicalYear());
        assertEquals(1, HistoricalYearParser.parse("1 AD", GREGORIAN).astronomicalYear());
    }

    @Test
    void retainsApproximationAndSourceExpression() {
        HistoricalDate date = HistoricalYearParser.parse("c. 1200 CE", GREGORIAN);
        assertTrue(date.approximate());
        assertEquals("c. 1200 CE", HistoricalDateFormatter.format(date));
    }

    @Test
    void rejectsYearZeroAndAmbiguousNumericOnlyInput() {
        assertThrows(IllegalArgumentException.class, () -> HistoricalYearParser.parse("0 BCE", GREGORIAN));
        assertThrows(IllegalArgumentException.class, () -> HistoricalYearParser.parse("1200", GREGORIAN));
    }
}
