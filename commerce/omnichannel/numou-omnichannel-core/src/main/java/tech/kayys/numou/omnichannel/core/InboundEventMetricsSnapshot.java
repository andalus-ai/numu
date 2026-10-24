package tech.kayys.numou.omnichannel.core;

import java.util.Map;
import java.util.Objects;

/** Immutable, low-cardinality snapshot suitable for health endpoints and tests. */
public record InboundEventMetricsSnapshot(
        Map<InboundWorkerDisposition, Long> outcomes,
        Map<InboundWorkerDisposition, Long> failures) {
    public InboundEventMetricsSnapshot {
        outcomes = Map.copyOf(Objects.requireNonNull(outcomes, "outcomes must not be null"));
        failures = Map.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
    }
}
