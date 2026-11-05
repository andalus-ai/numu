package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Extensible historical place taxonomy key (settlement, region, site, waterbody, etc.). */
public record HistoricalPlaceKindCode(String value) {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    public HistoricalPlaceKindCode {
        Objects.requireNonNull(value, "Place kind is required");
        value = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Place kind must be a 1-64 character lowercase code");
        }
    }
    public static HistoricalPlaceKindCode of(String value) { return new HistoricalPlaceKindCode(value); }
}
