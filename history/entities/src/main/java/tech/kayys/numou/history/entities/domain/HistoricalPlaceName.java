package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** A name assertion with optional language and historical validity. */
public record HistoricalPlaceName(
        String value,
        String languageTag,
        HistoricalDateRange validDuring,
        boolean preferred) {
    public HistoricalPlaceName {
        Objects.requireNonNull(value, "Place name is required");
        value = value.trim();
        if (value.isEmpty() || value.length() > 240) throw new IllegalArgumentException("Place name must contain 1-240 characters");
        languageTag = languageTag == null ? "" : languageTag.trim();
        if (languageTag.length() > 35) throw new IllegalArgumentException("Language tag cannot exceed 35 characters");
        if (!languageTag.isEmpty()) {
            try {
                languageTag = new Locale.Builder().setLanguageTag(languageTag).build().toLanguageTag();
            } catch (java.util.IllformedLocaleException exception) {
                throw new IllegalArgumentException("Place-name language tag must be a valid BCP 47 tag", exception);
            }
        }
    }

    public Optional<HistoricalDateRange> validity() {
        return Optional.ofNullable(validDuring);
    }
}
