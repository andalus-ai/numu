package tech.kayys.numou.history.chronology.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Calendar-aware comparison service. Calendar adapters normalize precision
 * intervals to a shared absolute-day axis before any cross-calendar comparison.
 */
public final class HistoricalChronology {
    private final Map<CalendarSystemId, CalendarDateConverter> converters;

    public HistoricalChronology(Collection<? extends CalendarDateConverter> converters) {
        Objects.requireNonNull(converters, "Calendar converters are required");
        Map<CalendarSystemId, CalendarDateConverter> registered = new HashMap<>();
        for (CalendarDateConverter converter : converters) {
            Objects.requireNonNull(converter, "Calendar converter cannot be null");
            CalendarDateConverter previous = registered.putIfAbsent(converter.calendarSystem(), converter);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate calendar converter: " + converter.calendarSystem().value());
            }
        }
        this.converters = Map.copyOf(registered);
    }

    public static HistoricalChronology standard() {
        return new HistoricalChronology(java.util.List.of(
                new GregorianCalendarDateConverter(),
                new JulianCalendarDateConverter()));
    }

    /**
     * Compares the full intervals implied by each date's precision.
     * Empty means a calendar adapter is not registered or cannot normalize it.
     */
    public Optional<TemporalRelation> compare(HistoricalDate left, HistoricalDate right) {
        Objects.requireNonNull(left, "Left date is required");
        Objects.requireNonNull(right, "Right date is required");
        Optional<AbsoluteDayRange> leftBounds = bounds(left);
        Optional<AbsoluteDayRange> rightBounds = bounds(right);
        if (leftBounds.isEmpty() || rightBounds.isEmpty()) return Optional.empty();

        AbsoluteDayRange a = leftBounds.orElseThrow();
        AbsoluteDayRange b = rightBounds.orElseThrow();
        if (a.lastDay() < b.firstDay()) return Optional.of(TemporalRelation.BEFORE);
        if (a.firstDay() > b.lastDay()) return Optional.of(TemporalRelation.AFTER);
        if (a.equals(b)) return Optional.of(TemporalRelation.SAME);
        return Optional.of(TemporalRelation.OVERLAPS);
    }

    /**
     * Converts an exact date to another registered calendar. Partial dates
     * return empty because a single target-calendar date cannot represent
     * their interval without changing precision semantics.
     */
    public Optional<HistoricalDate> convert(HistoricalDate source, CalendarSystemId targetCalendar) {
        Objects.requireNonNull(source, "Source date is required");
        Objects.requireNonNull(targetCalendar, "Target calendar is required");
        CalendarDateConverter sourceConverter = converters.get(source.calendarSystem());
        CalendarDateConverter targetConverter = converters.get(targetCalendar);
        if (sourceConverter == null || targetConverter == null) return Optional.empty();
        if (source.calendarSystem().equals(targetCalendar)) return Optional.of(source);
        java.util.OptionalLong absoluteDay = sourceConverter.toAbsoluteDay(source);
        if (absoluteDay.isEmpty()) return Optional.empty();
        HistoricalDate converted = targetConverter.fromAbsoluteDay(absoluteDay.getAsLong());
        if (!source.approximate()) return Optional.of(converted);
        return Optional.of(converted.asApproximate(""));
    }

    public Optional<AbsoluteDayRange> bounds(HistoricalDate date) {
        Objects.requireNonNull(date, "Date is required");
        CalendarDateConverter converter = converters.get(date.calendarSystem());
        return converter == null ? Optional.empty() : converter.toAbsoluteDayBounds(date);
    }
}
