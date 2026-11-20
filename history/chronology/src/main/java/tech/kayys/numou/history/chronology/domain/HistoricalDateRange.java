package tech.kayys.numou.history.chronology.domain;

import java.util.Objects;

/**
 * Closed historical date range. Endpoints retain their own precision and
 * uncertainty; the range does not invent a midpoint or silently normalize them.
 */
public record HistoricalDateRange(HistoricalDate from, HistoricalDate through) {
    public HistoricalDateRange {
        Objects.requireNonNull(from, "Range start is required");
        Objects.requireNonNull(through, "Range end is required");
        if (!from.calendarSystem().equals(through.calendarSystem())) {
            throw new IllegalArgumentException("Range endpoints must use the same calendar system");
        }
        if (from.compareNominallyTo(through) > 0) {
            throw new IllegalArgumentException("Range start must not be after range end");
        }
    }

    public CalendarSystemId calendarSystem() {
        return from.calendarSystem();
    }

    public boolean isSingleNominalDate() {
        return from.equals(through);
    }
}
