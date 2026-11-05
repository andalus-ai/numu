package tech.kayys.numou.history.chronology.domain;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Port implemented by calendar adapters to map historical dates to and from
 * the chronology engine's absolute-day axis. Implementations validate dates
 * according to their own calendar rules.
 */
public interface CalendarDateConverter {
    CalendarSystemId calendarSystem();

    /** Exact day conversion; partial dates return empty. */
    OptionalLong toAbsoluteDay(HistoricalDate date);

    /** Converts an absolute day to an exact date in this calendar. */
    HistoricalDate fromAbsoluteDay(long absoluteDay);

    /**
     * Inclusive absolute-day bounds for the precision supplied by the date.
     * A year or month expands to its calendar-defined first and last day.
     * Approximation flags do not expand these bounds because they carry no
     * quantified uncertainty interval.
     */
    Optional<AbsoluteDayRange> toAbsoluteDayBounds(HistoricalDate date);
}
