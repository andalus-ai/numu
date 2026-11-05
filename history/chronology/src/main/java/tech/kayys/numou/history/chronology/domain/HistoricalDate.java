package tech.kayys.numou.history.chronology.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A date in a named historical calendar.
 *
 * The year uses astronomical numbering: 1 CE = 1, 1 BCE = 0, 2 BCE = -1.
 * A zero month/day denotes omitted precision, not a literal calendar field.
 * This type deliberately does not convert calendars or assume Gregorian rules.
 */
public record HistoricalDate(
        long astronomicalYear,
        int month,
        int day,
        DatePrecision precision,
        CalendarSystemId calendarSystem,
        boolean approximate,
        String originalExpression) {

    public HistoricalDate {
        Objects.requireNonNull(precision, "Date precision is required");
        Objects.requireNonNull(calendarSystem, "Calendar system is required");
        originalExpression = originalExpression == null ? "" : originalExpression.trim();

        if (precision == DatePrecision.YEAR && (month != 0 || day != 0)) {
            throw new IllegalArgumentException("YEAR precision must not include month or day");
        }
        if (precision == DatePrecision.MONTH && (month < 1 || month > 13 || day != 0)) {
            throw new IllegalArgumentException("MONTH precision requires a month (1..13) and no day");
        }
        if (precision == DatePrecision.DAY && (month < 1 || month > 13 || day < 1 || day > 31)) {
            throw new IllegalArgumentException("DAY precision requires a month (1..13) and day (1..31)");
        }
    }

    public static HistoricalDate year(long astronomicalYear, CalendarSystemId calendar) {
        return new HistoricalDate(astronomicalYear, 0, 0, DatePrecision.YEAR, calendar, false, "");
    }

    public static HistoricalDate month(long astronomicalYear, int month, CalendarSystemId calendar) {
        return new HistoricalDate(astronomicalYear, month, 0, DatePrecision.MONTH, calendar, false, "");
    }

    public static HistoricalDate day(long astronomicalYear, int month, int day, CalendarSystemId calendar) {
        return new HistoricalDate(astronomicalYear, month, day, DatePrecision.DAY, calendar, false, "");
    }

    public HistoricalDate asApproximate(String sourceExpression) {
        return new HistoricalDate(astronomicalYear, month, day, precision, calendarSystem, true, sourceExpression);
    }

    public Optional<String> sourceExpression() {
        return originalExpression.isBlank() ? Optional.empty() : Optional.of(originalExpression);
    }

    /**
     * Compares nominal fields only and only within the same calendar.
     * It does not resolve uncertain dates or convert between calendars.
     */
    public int compareNominallyTo(HistoricalDate other) {
        Objects.requireNonNull(other, "Other date is required");
        if (!calendarSystem.equals(other.calendarSystem)) {
            throw new IllegalArgumentException("Cannot compare dates from different calendars without conversion");
        }
        int yearComparison = Long.compare(astronomicalYear, other.astronomicalYear);
        if (yearComparison != 0) return yearComparison;
        int monthComparison = Integer.compare(month, other.month);
        if (monthComparison != 0) return monthComparison;
        return Integer.compare(day, other.day);
    }

    public String eraLabel() {
        return astronomicalYear <= 0 ? "BCE" : "CE";
    }

    /** Converts astronomical numbering to a human-facing BCE/CE year; no year zero is displayed. */
    public long displayedYearOfEra() {
        return astronomicalYear <= 0 ? 1L - astronomicalYear : astronomicalYear;
    }
}
