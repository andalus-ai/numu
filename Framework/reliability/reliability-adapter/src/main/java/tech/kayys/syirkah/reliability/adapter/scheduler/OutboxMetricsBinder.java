package tech.kayys.syirkah.reliability.adapter.scheduler;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import tech.kayys.syirkah.observability.MetricName;
import tech.kayys.syirkah.observability.MetricsPort;
import tech.kayys.syirkah.reliability.adapter.postgres.PgOutboxStore;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes outbox health as gauges: {@code outbox.pending}, {@code outbox.dead_letters} and
 * {@code outbox.oldest_pending_age_seconds} (the lag SLI). Gauges are sampled on scrape and must never touch the
 * database, so a scheduled job refreshes cached values every 15 s. NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class OutboxMetricsBinder {

    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong dead = new AtomicLong();
    private final AtomicLong oldestAge = new AtomicLong();

    @Inject
    PgOutboxStore store;
    @Inject
    MetricsPort metrics;

    void register(@Observes StartupEvent ev) {
        metrics.gauge(MetricName.of("outbox.pending"), pending::get);
        metrics.gauge(MetricName.of("outbox.dead_letters"), dead::get);
        metrics.gauge(MetricName.of("outbox.oldest_pending_age_seconds"), oldestAge::get);
    }

    @Scheduled(every = "15s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "outbox-metrics")
    Uni<Void> refresh() {
        return store.countPending().invoke(pending::set)
                .flatMap(x -> store.countDeadLetters().invoke(dead::set))
                .flatMap(x -> store.oldestPendingAgeSeconds().invoke(oldestAge::set))
                .onFailure().recoverWithNull().replaceWithVoid();
    }
}
