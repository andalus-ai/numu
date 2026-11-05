package tech.kayys.numou.history.chronology.domain;

import java.util.OptionalLong;

/**
 * Port implemented by calendar adapters to map a fully specified calendar date
 * to an absolute day number. Partial dates are intentionally not convertible.
 */
public interface CalendarDateConverter {
    CalendarSystemId calendarSystem();

    /**
     * Returns an absolute day number when the calendar adapter can convert the
     * exact date. Returns empty for partial dates; rejects dates invalid in its
     * calendar rather than coercing them.
     */
    OptionalLong toAbsoluteDay(HistoricalDate date);
}
