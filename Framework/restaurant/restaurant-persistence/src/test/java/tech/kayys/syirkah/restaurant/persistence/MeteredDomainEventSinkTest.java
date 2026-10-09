package tech.kayys.syirkah.restaurant.persistence;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.observability.MetricName;
import tech.kayys.syirkah.observability.MetricsPort;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeteredDomainEventSinkTest {

    record Ev(UUID eventId, Instant occurredAt, String eventType) implements DomainEvent { }

    static final class Metrics implements MetricsPort {
        final List<MetricName> counted = new ArrayList<>();
        public void increment(MetricName n, long a) { counted.add(n); }
        public void record(MetricName n, Duration d) { }
        public void gauge(MetricName n, Supplier<Number> v) { }
    }

    final Metrics metrics = new Metrics();

    private static Ev ev(String type) { return new Ev(UUID.randomUUID(), Instant.now(), type); }

    @Test
    void countsEachEventByTypeAfterTheDelegateSucceeds() {
        final var sink = new MeteredDomainEventSink((t, e) -> Uni.createFrom().voidItem(), metrics);
        sink.publish(UUID.randomUUID(), List.of(ev("restaurant.reservation.no-show"), ev("restaurant.order.paid"))).await().indefinitely();
        assertThat(metrics.counted).extracting(m -> m.tags().get("type")).containsExactly("restaurant.reservation.no-show", "restaurant.order.paid");
        assertThat(metrics.counted).allMatch(m -> m.tags().keySet().equals(Set.of("type"))); // no tenant/id tags: bounded cardinality
    }

    @Test
    void failedPublishIsNotCounted() {
        final var sink = new MeteredDomainEventSink((t, e) -> Uni.createFrom().failure(new IllegalStateException("db down")), metrics);
        assertThatThrownBy(() -> sink.publish(UUID.randomUUID(), List.of(ev("restaurant.order.paid"))).await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
        assertThat(metrics.counted).isEmpty();
    }
}
