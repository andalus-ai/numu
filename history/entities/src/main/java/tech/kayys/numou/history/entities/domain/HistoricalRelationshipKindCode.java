package tech.kayys.numou.history.entities.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, namespaced-friendly relationship vocabulary key. */
public record HistoricalRelationshipKindCode(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9._-]{1,63}");

    public HistoricalRelationshipKindCode {
        Objects.requireNonNull(value, "Relationship kind is required");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Relationship kind must be 2-64 lowercase key characters");
        }
    }

    public static HistoricalRelationshipKindCode of(String value) {
        return new HistoricalRelationshipKindCode(value);
    }
}
