package tech.kayys.numou.history.entities.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for a historical person record. */
public record HistoricalPersonId(UUID value) {
    public HistoricalPersonId {
        Objects.requireNonNull(value, "Historical person ID is required");
    }
    public static HistoricalPersonId generate() { return new HistoricalPersonId(UUID.randomUUID()); }
    public static HistoricalPersonId of(UUID value) { return new HistoricalPersonId(value); }
}
