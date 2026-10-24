package tech.kayys.numou.omnichannel.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Dependency-free metrics implementation for embedded deployments and tests.
 * Production adapters can export the same low-cardinality values to a metrics backend.
 */
public final class InMemoryInboundEventMetrics implements InboundEventMetrics {
    private final Map<InboundWorkerDisposition, LongAdder> outcomes = counters();
    private final Map<InboundWorkerDisposition, LongAdder> failures = counters();

    @Override
    public void recordOutcome(InboundWorkerDisposition disposition) {
        outcomes.get(requireDisposition(disposition)).increment();
    }

    @Override
    public void recordFailure(InboundWorkerDisposition disposition) {
        failures.get(requireDisposition(disposition)).increment();
    }

    public InboundEventMetricsSnapshot snapshot() {
        return new InboundEventMetricsSnapshot(snapshotOf(outcomes), snapshotOf(failures));
    }

    private static InboundWorkerDisposition requireDisposition(InboundWorkerDisposition disposition) {
        if (disposition == null) throw new IllegalArgumentException("disposition must not be null");
        return disposition;
    }

    private static Map<InboundWorkerDisposition, LongAdder> counters() {
        Map<InboundWorkerDisposition, LongAdder> values = new EnumMap<>(InboundWorkerDisposition.class);
        for (InboundWorkerDisposition disposition : InboundWorkerDisposition.values()) {
            values.put(disposition, new LongAdder());
        }
        return values;
    }

    private static Map<InboundWorkerDisposition, Long> snapshotOf(
            Map<InboundWorkerDisposition, LongAdder> counters) {
        Map<InboundWorkerDisposition, Long> values = new EnumMap<>(InboundWorkerDisposition.class);
        counters.forEach((key, value) -> values.put(key, value.sum()));
        return Map.copyOf(values);
    }
}
