package tech.kayys.numou.affiliate.runtime;

import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.OutboxHealthSnapshot;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Stable low-cardinality Affiliate outbox telemetry; never tags tenant or event identifiers. */
public final class AffiliateOutboxMetrics {
    private final MetricsPort metrics;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong pendingDue = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicLong expiredLeases = new AtomicLong();
    private final AtomicLong deadLetters = new AtomicLong();
    private final AtomicLong oldestDueAgeSeconds = new AtomicLong();

    public AffiliateOutboxMetrics(MetricsPort metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        metrics.gauge(MetricName.of("affiliate.outbox.pending"), pending::get);
        metrics.gauge(MetricName.of("affiliate.outbox.pending.due"), pendingDue::get);
        metrics.gauge(MetricName.of("affiliate.outbox.inflight"), inFlight::get);
        metrics.gauge(MetricName.of("affiliate.outbox.expired.leases"), expiredLeases::get);
        metrics.gauge(MetricName.of("affiliate.outbox.deadletter"), deadLetters::get);
        metrics.gauge(MetricName.of("affiliate.outbox.oldest.due.age.seconds"), oldestDueAgeSeconds::get);
    }

    public void recordDelivered(long count) {
        if (count > 0) metrics.increment(MetricName.of("affiliate.outbox.delivered"), count);
    }

    public void recordDispatchFailure() {
        metrics.increment(MetricName.of("affiliate.outbox.dispatch.failure"));
    }

    public void refresh(OutboxHealthSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        pending.set(snapshot.pending());
        pendingDue.set(snapshot.pendingDue());
        inFlight.set(snapshot.inFlight());
        expiredLeases.set(snapshot.expiredLeases());
        deadLetters.set(snapshot.deadLetter());
        oldestDueAgeSeconds.set(snapshot.oldestDueAgeSeconds());
    }
}