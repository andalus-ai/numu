package tech.kayys.numou.aggregate.persistence;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;
import tech.kayys.numou.observability.SafeTags;
import tech.kayys.numou.aggregate.spi.DomainEventSink;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Decorator that turns the domain event stream into business metrics, with zero change to the domain code:
 * {@code domain.events.published{type=restaurant.reservation.no-show}} etc. The {@code type} tag has a small fixed
 * vocabulary (the event catalogue), so cardinality is bounded. Metrics are recorded only after the delegate
 * succeeded, so a rolled-back transaction does not count as a business event.
 */
public final class MeteredDomainEventSink implements DomainEventSink {

    private static final MetricName PUBLISHED = MetricName.of("domain.events.published");

    private final DomainEventSink delegate;
    private final MetricsPort metrics;

    public MeteredDomainEventSink(DomainEventSink delegate, MetricsPort metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    @Override
    public Uni<Void> publish(UUID tenantId, List<DomainEvent> events) {
        return delegate.publish(tenantId, events).invoke(() ->
                events.forEach(e -> metrics.increment(SafeTags.apply(PUBLISHED, SafeTags.of("type", e.eventType())))));
    }
}
