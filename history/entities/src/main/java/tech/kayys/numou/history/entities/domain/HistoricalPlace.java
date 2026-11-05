package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable identity for a historical place. Names, boundaries and coordinate
 * assertions are time-varying facts rather than identity fields.
 */
public record HistoricalPlace(
        HistoricalPlaceId id,
        HistoricalPlaceKindCode kind,
        List<HistoricalPlaceName> names,
        List<HistoricalPlaceLocation> locations,
        HistoricalDateRange existenceRange,
        HistoricalPlaceId parentPlaceId,
        long revision,
        Instant recordedAt,
        Instant updatedAt) {

    public HistoricalPlace {
        Objects.requireNonNull(id, "Place ID is required");
        Objects.requireNonNull(kind, "Place kind is required");
        names = List.copyOf(Objects.requireNonNull(names, "Place names are required"));
        if (names.isEmpty()) throw new IllegalArgumentException("A historical place must have at least one name");
        if (names.size() > 64) throw new IllegalArgumentException("A historical place may have at most 64 names");
        locations = List.copyOf(Objects.requireNonNull(locations, "Place locations are required"));
        if (locations.size() > 64) throw new IllegalArgumentException("A historical place may have at most 64 location assertions");
        if (id.equals(parentPlaceId)) throw new IllegalArgumentException("A place cannot be its own parent");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(recordedAt, "Record timestamp is required");
        Objects.requireNonNull(updatedAt, "Update timestamp is required");
        if (updatedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Update timestamp cannot precede record timestamp");
    }

    public static HistoricalPlace create(
            HistoricalPlaceKindCode kind, List<HistoricalPlaceName> names,
            List<HistoricalPlaceLocation> locations, HistoricalDateRange existenceRange,
            HistoricalPlaceId parentId, Instant recordedAt) {
        return new HistoricalPlace(HistoricalPlaceId.generate(), kind, names, locations,
                existenceRange, parentId, 1, recordedAt, recordedAt);
    }

    public HistoricalPlace revise(
            HistoricalPlaceKindCode newKind, List<HistoricalPlaceName> newNames,
            List<HistoricalPlaceLocation> newLocations, HistoricalDateRange newExistenceRange,
            HistoricalPlaceId newParentId, Instant revisedAt) {
        Objects.requireNonNull(revisedAt, "Revision timestamp is required");
        if (revisedAt.isBefore(updatedAt)) throw new IllegalArgumentException("Revision timestamp cannot precede update timestamp");
        return new HistoricalPlace(id, newKind, newNames, newLocations, newExistenceRange,
                newParentId, Math.incrementExact(revision), recordedAt, revisedAt);
    }

    public Optional<HistoricalDateRange> existenceDates() { return Optional.ofNullable(existenceRange); }
    public Optional<HistoricalPlaceId> parent() { return Optional.ofNullable(parentPlaceId); }
}
