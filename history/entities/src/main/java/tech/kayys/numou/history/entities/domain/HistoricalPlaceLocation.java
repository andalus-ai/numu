package tech.kayys.numou.history.entities.domain;

import tech.kayys.numou.geo.domain.GeoPoint;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;

import java.util.Objects;
import java.util.Optional;

/** A time-bounded location assertion using the shared Geo WGS84 primitive. */
public record HistoricalPlaceLocation(
        GeoPoint point,
        HistoricalPlacePrecision precision,
        HistoricalDateRange validDuring,
        String note) {
    public HistoricalPlaceLocation {
        Objects.requireNonNull(point, "Geo point is required");
        Objects.requireNonNull(precision, "Location precision is required");
        note = note == null ? "" : note.trim();
        if (note.length() > 1_000) throw new IllegalArgumentException("Location note cannot exceed 1000 characters");
    }

    public Optional<HistoricalDateRange> validity() {
        return Optional.ofNullable(validDuring);
    }
}
