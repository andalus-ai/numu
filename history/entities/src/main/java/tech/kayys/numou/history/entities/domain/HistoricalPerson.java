package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDate;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Historical person identity and biographical date assertions.
 * Names and dates are editorial assertions; source/evidence attribution is owned by M5.
 */
public record HistoricalPerson(
        HistoricalPersonId id,
        String primaryName,
        Set<String> alternativeNames,
        HistoricalDate birthDate,
        HistoricalDate deathDate,
        String biography,
        long revision,
        Instant recordedAt,
        Instant updatedAt) {

    public HistoricalPerson {
        Objects.requireNonNull(id, "Person ID is required");
        primaryName = requireText(primaryName, "Primary name", 240);
        alternativeNames = normalizeNames(alternativeNames);
        if (alternativeNames.contains(primaryName.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Alternative names must not duplicate the primary name");
        }
        biography = biography == null ? "" : biography.trim();
        if (biography.length() > 8_000) throw new IllegalArgumentException("Biography cannot exceed 8000 characters");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
    }

    public static HistoricalPerson create(
            String primaryName, Collection<String> alternativeNames,
            HistoricalDate birthDate, HistoricalDate deathDate, String biography, Instant recordedAt) {
        return new HistoricalPerson(HistoricalPersonId.generate(), primaryName,
                alternativeNames == null ? Set.of() : new TreeSet<>(alternativeNames),
                birthDate, deathDate, biography, 1, recordedAt, recordedAt);
    }

    public HistoricalPerson revise(
            String newPrimaryName, Collection<String> newAlternativeNames,
            HistoricalDate newBirthDate, HistoricalDate newDeathDate,
            String newBiography, Instant revisedAt) {
        Objects.requireNonNull(revisedAt, "Revision timestamp is required");
        if (revisedAt.isBefore(updatedAt)) throw new IllegalArgumentException("Revision timestamp cannot precede update timestamp");
        return new HistoricalPerson(id, newPrimaryName,
                newAlternativeNames == null ? Set.of() : new TreeSet<>(newAlternativeNames),
                newBirthDate, newDeathDate, newBiography, Math.incrementExact(revision), recordedAt, revisedAt);
    }

    public Optional<HistoricalDate> birth() { return Optional.ofNullable(birthDate); }
    public Optional<HistoricalDate> death() { return Optional.ofNullable(deathDate); }

    private static Set<String> normalizeNames(Collection<String> supplied) {
        Objects.requireNonNull(supplied, "Alternative names are required");
        if (supplied.size() > 32) throw new IllegalArgumentException("At most 32 alternative names are allowed");
        TreeSet<String> normalized = new TreeSet<>();
        for (String name : supplied) {
            String value = requireText(name, "Alternative name", 240);
            normalized.add(value.toLowerCase(java.util.Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String requireText(String value, String label, int max) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(label + " must contain 1-" + max + " characters");
        return normalized;
    }
}
