package tech.kayys.numou.history.chronology.domain;

import java.util.Objects;

/** Calendar-neutral label formatter; localized month/day names belong to calendar adapters. */
public final class HistoricalDateFormatter {
    private HistoricalDateFormatter() {
    }

    public static String formatYear(HistoricalDate date) {
        Objects.requireNonNull(date, "Date is required");
        String label = date.displayedYearOfEra() + " " + date.eraLabel();
        return date.approximate() ? "c. " + label : label;
    }

    /** Uses a preserved source expression when available, otherwise a numeric, unlocalized label. */
    public static String format(HistoricalDate date) {
        Objects.requireNonNull(date, "Date is required");
        if (date.sourceExpression().isPresent()) {
            return date.sourceExpression().orElseThrow();
        }
        String label = switch (date.precision()) {
            case YEAR -> formatYear(date);
            case MONTH -> date.month() + " " + formatYear(date);
            case DAY -> date.day() + "/" + date.month() + "/" + formatYear(date);
        };
        if (date.approximate() && date.precision() != DatePrecision.YEAR) {
            return "c. " + label;
        }
        return label;
    }
}
