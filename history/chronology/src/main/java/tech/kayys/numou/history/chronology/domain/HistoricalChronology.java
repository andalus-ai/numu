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
        return compareBounds(leftBounds.orElseThrow(), rightBounds.orElseThrow());
    }

    /**
     * Compares two closed historical ranges using the full precision bounds
     * of their endpoints. Calendars may differ; missing adapters return empty.
     */
    public Optional<TemporalRelation> compare(HistoricalDateRange left, HistoricalDateRange right) {
        Objects.requireNonNull(left, "Left date range is required");
        Objects.requireNonNull(right, "Right date range is required");
        Optional<AbsoluteDayRange> leftBounds = rangeBounds(left);
        Optional<AbsoluteDayRange> rightBounds = rangeBounds(right);
        if (leftBounds.isEmpty() || rightBounds.isEmpty()) return Optional.empty();
        return compareBounds(leftBounds.orElseThrow(), rightBounds.orElseThrow());
    }

    /** Returns whether the inner range is fully contained by the outer range. */
    public Optional<Boolean> contains(HistoricalDateRange outer, HistoricalDateRange inner) {
        Objects.requireNonNull(outer, "Outer date range is required");
        Objects.requireNonNull(inner, "Inner date range is required");
        Optional<AbsoluteDayRange> outerBounds = rangeBounds(outer);
        Optional<AbsoluteDayRange> innerBounds = rangeBounds(inner);
        if (outerBounds.isEmpty() || innerBounds.isEmpty()) return Optional.empty();
        AbsoluteDayRange a = outerBounds.orElseThrow();
        AbsoluteDayRange b = innerBounds.orElseThrow();
        return Optional.of(a.firstDay() <= b.firstDay() && a.lastDay() >= b.lastDay());
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

    private Optional<AbsoluteDayRange> rangeBounds(HistoricalDateRange range) {
        Optional<AbsoluteDayRange> start = bounds(range.from());
        Optional<AbsoluteDayRange> end = bounds(range.through());
        if (start.isEmpty() || end.isEmpty()) return Optional.empty();
        long firstDay = start.orElseThrow().firstDay();
        long lastDay = end.orElseThrow().lastDay();
        if (firstDay > lastDay) {
            throw new IllegalArgumentException("Historical date range resolves to an inverted absolute-day interval");
        }
        return Optional.of(new AbsoluteDayRange(firstDay, lastDay));
    }

    private static Optional<TemporalRelation> compareBounds(AbsoluteDayRange left, AbsoluteDayRange right) {
        if (left.lastDay() < right.firstDay()) return Optional.of(TemporalRelation.BEFORE);
        if (left.firstDay() > right.lastDay()) return Optional.of(TemporalRelation.AFTER);
        if (left.equals(right)) return Optional.of(TemporalRelation.SAME);
        return Optional.of(TemporalRelation.OVERLAPS);
    }
}
