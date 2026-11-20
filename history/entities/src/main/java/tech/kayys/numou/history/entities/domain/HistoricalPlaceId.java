package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a historical place independent of names and boundary changes. */
public record HistoricalPlaceId(UUID value) {
    public HistoricalPlaceId {
        Objects.requireNonNull(value, "Historical place ID is required");
    }
    public static HistoricalPlaceId generate() { return new HistoricalPlaceId(UUID.randomUUID()); }
    public static HistoricalPlaceId of(UUID value) { return new HistoricalPlaceId(value); }
}
