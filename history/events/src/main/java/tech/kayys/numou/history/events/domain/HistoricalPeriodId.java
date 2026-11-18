package tech.kayys.numou.history.events.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a historical period, independent of its timeline appearances. */
public record HistoricalPeriodId(UUID value) {
    public HistoricalPeriodId {
        Objects.requireNonNull(value, "Historical period ID is required");
    }

    public static HistoricalPeriodId generate() {
        return new HistoricalPeriodId(UUID.randomUUID());
    }

    public static HistoricalPeriodId of(UUID value) {
        return new HistoricalPeriodId(value);
    }
}
