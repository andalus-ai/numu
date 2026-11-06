package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a historical collective record. */
public record HistoricalCollectiveId(UUID value) {
    public HistoricalCollectiveId {
        Objects.requireNonNull(value, "Historical collective ID is required");
    }
    public static HistoricalCollectiveId generate() { return new HistoricalCollectiveId(UUID.randomUUID()); }
    public static HistoricalCollectiveId of(UUID value) { return new HistoricalCollectiveId(value); }
}
