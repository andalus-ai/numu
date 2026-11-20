package tech.kayys.numou.history.chronology.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identifier for a calendar system (for example, gregorian or julian). */
public record CalendarSystemId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9._-]{1,63}");

    public CalendarSystemId {
        Objects.requireNonNull(value, "Calendar system id is required");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid calendar system id: " + value);
        }
    }

    public static CalendarSystemId of(String value) {
        return new CalendarSystemId(value);
    }
}
