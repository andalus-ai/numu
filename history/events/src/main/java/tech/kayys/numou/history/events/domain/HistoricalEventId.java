package tech.kayys.numou.history.events.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a canonical historical event, independent of timeline projections. */
public record HistoricalEventId(UUID value) {
    public HistoricalEventId {
        Objects.requireNonNull(value, "Historical event ID is required");
    }

    public static HistoricalEventId generate() {
        return new HistoricalEventId(UUID.randomUUID());
    }

    public static HistoricalEventId of(UUID value) {
        return new HistoricalEventId(value);
    }
}
