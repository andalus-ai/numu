package tech.kayys.numou.history.events.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, taxonomy-neutral classification key; display labels belong to catalogues. */
public record HistoricalCategoryCode(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public HistoricalCategoryCode {
        Objects.requireNonNull(value, "Category code is required");
        value = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Category code must be 1-64 lowercase ASCII letters, digits, '.', '_' or '-'");
        }
    }

    public static HistoricalCategoryCode of(String value) {
        return new HistoricalCategoryCode(value);
    }
}
