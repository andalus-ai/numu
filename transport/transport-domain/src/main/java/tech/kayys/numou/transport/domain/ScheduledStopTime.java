package tech.kayys.numou.transport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Planned arrival/departure instants for one stop on a scheduled service. */
public record ScheduledStopTime(UUID stopId, int sequence, Instant arrivalAt, Instant departureAt) {
    public ScheduledStopTime {
        Objects.requireNonNull(stopId, "stopId");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (arrivalAt == null && departureAt == null) {
            throw new IllegalArgumentException("a stop time must define arrival, departure, or both");
        }
        if (arrivalAt != null && departureAt != null && departureAt.isBefore(arrivalAt)) {
            throw new IllegalArgumentException("departureAt must not be before arrivalAt");
        }
    }
}
