package tech.kayys.numou.pos.adapter.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Low-cardinality metrics for delivery passes and durable outbox backlog states. */
@ApplicationScoped
public class PosOutboxMetrics {
    private final MeterRegistry registry;
    private final PosOutboxOperations operations;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();
    private Counter successfulPasses;
    private Counter failedPasses;
    private Counter publishedEvents;

    @Inject
    public PosOutboxMetrics(MeterRegistry registry, PosOutboxOperations operations) {
        this.registry = registry;
        this.operations = operations;
    }

    @PostConstruct
    void register() {
        successfulPasses = Counter.builder("numou.pos.outbox.dispatch.passes")
                .tag("outcome", "success").register(registry);
        failedPasses = Counter.builder("numou.pos.outbox.dispatch.passes")
                .tag("outcome", "failure").register(registry);
        publishedEvents = Counter.builder("numou.pos.outbox.events.published").register(registry);
        Gauge.builder("numou.pos.outbox.events", pending, AtomicLong::get).tag("status", "pending").register(registry);
        Gauge.builder("numou.pos.outbox.events", inFlight, AtomicLong::get).tag("status", "in_flight").register(registry);
        Gauge.builder("numou.pos.outbox.events", published, AtomicLong::get).tag("status", "published").register(registry);
        Gauge.builder("numou.pos.outbox.events", dead, AtomicLong::get).tag("status", "dead").register(registry);
        refreshBacklog();
    }

    public void passSucceeded(int count) {
        successfulPasses.increment();
        if (count > 0) publishedEvents.increment(count);
        refreshBacklog();
    }

    public void passFailed() {
        failedPasses.increment();
    }

    private void refreshBacklog() {
        Map<String, Long> counts = operations.countByDeliveryStatus();
        pending.set(counts.getOrDefault("PENDING", 0L));
        inFlight.set(counts.getOrDefault("IN_FLIGHT", 0L));
        published.set(counts.getOrDefault("PUBLISHED", 0L));
        dead.set(counts.getOrDefault("DEAD", 0L));
    }
}
