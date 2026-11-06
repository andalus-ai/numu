package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Extensible collective taxonomy key; labels and localized descriptions live in catalogues. */
public record HistoricalEntityKindCode(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    public HistoricalEntityKindCode {
        Objects.requireNonNull(value, "Entity kind is required");
        value = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Entity kind must be a 1-64 character lowercase code");
        }
    }
    public static HistoricalEntityKindCode of(String value) { return new HistoricalEntityKindCode(value); }
}
