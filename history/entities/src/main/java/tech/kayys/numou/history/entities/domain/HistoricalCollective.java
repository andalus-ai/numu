package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Collective historical entity: civilization, culture, polity, dynasty,
 * institution, organization, confederation, or another catalogued kind.
 */
public record HistoricalCollective(
        HistoricalCollectiveId id,
        String primaryName,
        Set<String> alternativeNames,
        HistoricalEntityKindCode kind,
        HistoricalDateRange activeRange,
        HistoricalCollectiveId parentCollectiveId,
        String description,
        long revision,
        Instant recordedAt,
        Instant updatedAt) {

    public HistoricalCollective {
        Objects.requireNonNull(id, "Collective ID is required");
        primaryName = requireText(primaryName, "Primary name", 240);
        alternativeNames = normalizeNames(alternativeNames);
        for (String alternativeName : alternativeNames) {
            if (alternativeName.equalsIgnoreCase(primaryName)) {
                throw new IllegalArgumentException("Alternative names must not duplicate the primary name");
            }
        }
        Objects.requireNonNull(kind, "Collective kind is required");
        if (id.equals(parentCollectiveId)) throw new IllegalArgumentException("A collective cannot be its own parent");
        description = description == null ? "" : description.trim();
        if (description.length() > 8_000) throw new IllegalArgumentException("Description cannot exceed 8000 characters");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
    }

    public static HistoricalCollective create(
            String primaryName, Collection<String> alternativeNames,
            HistoricalEntityKindCode kind, HistoricalDateRange activeRange,
            HistoricalCollectiveId parentId, String description, Instant recordedAt) {
        return new HistoricalCollective(HistoricalCollectiveId.generate(), primaryName,
                asSet(alternativeNames), kind, activeRange, parentId, description, 1, recordedAt, recordedAt);
    }

    public HistoricalCollective revise(
            String newPrimaryName, Collection<String> newAlternativeNames,
            HistoricalEntityKindCode newKind, HistoricalDateRange newActiveRange,
            HistoricalCollectiveId newParentId, String newDescription, Instant revisedAt) {
        Objects.requireNonNull(revisedAt, "Revision timestamp is required");
        if (revisedAt.isBefore(updatedAt)) throw new IllegalArgumentException("Revision timestamp cannot precede update timestamp");
        return new HistoricalCollective(id, newPrimaryName, asSet(newAlternativeNames),
                newKind, newActiveRange, newParentId, newDescription,
                Math.incrementExact(revision), recordedAt, revisedAt);
    }

    public Optional<HistoricalDateRange> activeDates() { return Optional.ofNullable(activeRange); }
    public Optional<HistoricalCollectiveId> parent() { return Optional.ofNullable(parentCollectiveId); }

    private static Set<String> asSet(Collection<String> names) {
        return names == null ? Set.of() : new LinkedHashSet<>(names);
    }

    private static Set<String> normalizeNames(Collection<String> supplied) {
        Objects.requireNonNull(supplied, "Alternative names are required");
        if (supplied.size() > 32) throw new IllegalArgumentException("At most 32 alternative names are allowed");
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String name : supplied) {
            String value = requireText(name, "Alternative name", 240);
            normalized.putIfAbsent(value.toLowerCase(java.util.Locale.ROOT), value);
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(normalized.values()));
    }

    private static String requireText(String value, String label, int max) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(label + " must contain 1-" + max + " characters");
        return normalized;
    }
}
