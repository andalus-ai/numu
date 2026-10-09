package tech.kayys.syirkah.observability.adapter;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.observability.MetricName;
import tech.kayys.syirkah.observability.MetricsPort;

import java.time.Duration;
import java.util.function.Supplier;

/** {@link MetricsPort} on Micrometer (exported as Prometheus and/or OTLP by Quarkus). NOT COMPILED in sandbox. */
@ApplicationScoped
public class MicrometerMetricsAdapter implements MetricsPort {

    @Inject
    MeterRegistry registry;

    @Override
    public void increment(MetricName name, long amount) {
        registry.counter(name.name(), tags(name)).increment(amount);
    }

    @Override
    public void record(MetricName name, Duration duration) {
        registry.timer(name.name(), tags(name)).record(duration);
    }

    @Override
    public void gauge(MetricName name, Supplier<Number> value) {
        Gauge.builder(name.name(), () -> value.get().doubleValue()).tags(tags(name)).strongReference(true).register(registry);
    }

    private static Tags tags(MetricName n) {
        final var out = new java.util.ArrayList<io.micrometer.core.instrument.Tag>();
        n.tags().forEach((k, v) -> out.add(io.micrometer.core.instrument.Tag.of(k, v)));
        return Tags.of(out);
    }
}
