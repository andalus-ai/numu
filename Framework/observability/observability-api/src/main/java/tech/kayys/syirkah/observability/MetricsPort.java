package tech.kayys.syirkah.observability;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Metrics sink. Implemented by Micrometer (Prometheus/OTLP) in the observability
 * adapter; tests use a recording fake.
 *
 * <p>Cardinality rule: tags must come from {@link SafeTags}. Tenant ids, user ids,
 * reservation ids and table numbers are unbounded and will blow up a time-series
 * database; put them on traces and logs instead.
 */
public interface MetricsPort {

    void increment(MetricName name, long amount);

    default void increment(MetricName name) {
        increment(name, 1L);
    }

    void record(MetricName name, Duration duration);

    /** Registers (or replaces) a gauge sampled on scrape. */
    void gauge(MetricName name, Supplier<Number> value);
}
