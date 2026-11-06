package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A time-bounded, typed assertion connecting canonical historical records.
 * This is domain data, not proof: source claims and verification are added by M5.
 */
public record HistoricalRelationship(
        HistoricalRelationshipId id,
        HistoricalEntityReference subject,
        HistoricalEntityReference object,
        HistoricalRelationshipKindCode kind,
        HistoricalDateRange validDuring,
        String description,
        long revision,
        Instant recordedAt,
        Instant updatedAt) {

    public HistoricalRelationship {
        Objects.requireNonNull(id, "Relationship ID is required");
        Objects.requireNonNull(subject, "Relationship subject is required");
        Objects.requireNonNull(object, "Relationship object is required");
        Objects.requireNonNull(kind, "Relationship kind is required");
        if (subject.equals(object)) throw new IllegalArgumentException("A relationship cannot connect an entity to itself");
        description = description == null ? "" : description.trim();
        if (description.length() > 2_000) throw new IllegalArgumentException("Relationship description cannot exceed 2000 characters");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
    }

    public static HistoricalRelationship create(HistoricalEntityReference subject,
            HistoricalEntityReference object, HistoricalRelationshipKindCode kind,
            HistoricalDateRange validDuring, String description, Instant recordedAt,
            HistoricalRelationshipVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "Relationship vocabulary is required")
                .require(Objects.requireNonNull(kind, "Relationship kind is required"))
                .validateEndpoints(subject, object);
        return new HistoricalRelationship(HistoricalRelationshipId.generate(), subject, object, kind,
                validDuring, description, 1, recordedAt, recordedAt);
    }

    public HistoricalRelationship revise(HistoricalEntityReference newSubject,
            HistoricalEntityReference newObject, HistoricalRelationshipKindCode newKind,
            HistoricalDateRange newValidDuring, String newDescription, Instant revisedAt,
            HistoricalRelationshipVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "Relationship vocabulary is required")
                .require(Objects.requireNonNull(newKind, "Relationship kind is required"))
                .validateEndpoints(newSubject, newObject);
        Objects.requireNonNull(revisedAt, "Revision timestamp is required");
        if (revisedAt.isBefore(updatedAt)) throw new IllegalArgumentException("Revision timestamp cannot precede update timestamp");
        return new HistoricalRelationship(id, newSubject, newObject, newKind, newValidDuring,
                newDescription, Math.incrementExact(revision), recordedAt, revisedAt);
    }

    public Optional<HistoricalDateRange> validity() { return Optional.ofNullable(validDuring); }
}
