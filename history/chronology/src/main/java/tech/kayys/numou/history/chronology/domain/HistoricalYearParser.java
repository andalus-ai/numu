package tech.kayys.numou.history.chronology.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses explicit year-of-era labels such as "44 BCE" and "2026 CE".
 * Ambiguous numeric-only strings are intentionally rejected.
 */
public final class HistoricalYearParser {
    private static final Pattern YEAR = Pattern.compile(
            "^(c\\.\\s*)?([0-9]+)\\s*(BCE|BC|CE|AD)$",
            Pattern.CASE_INSENSITIVE);

    private HistoricalYearParser() {
    }

    public static HistoricalDate parse(String expression, CalendarSystemId calendarSystem) {
        Objects.requireNonNull(expression, "Expression is required");
        Objects.requireNonNull(calendarSystem, "Calendar system is required");
        String normalized = expression.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = YEAR.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Expected an explicit year and era, for example '44 BCE'");
        }

        long yearOfEra;
        try {
            yearOfEra = Long.parseLong(matcher.group(2));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Year is outside the supported range", exception);
        }
        if (yearOfEra < 1) {
            throw new IllegalArgumentException("BCE/CE year labels start at 1; there is no displayed year zero");
        }

        String era = matcher.group(3);
        long astronomicalYear;
        try {
            astronomicalYear = (era.equals("BCE") || era.equals("BC"))
                    ? Math.subtractExact(1L, yearOfEra)
                    : yearOfEra;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Year is outside the supported range", exception);
        }

        boolean approximate = matcher.group(1) != null;
        return new HistoricalDate(astronomicalYear, 0, 0, DatePrecision.YEAR,
                calendarSystem, approximate, expression.trim());
    }
}
