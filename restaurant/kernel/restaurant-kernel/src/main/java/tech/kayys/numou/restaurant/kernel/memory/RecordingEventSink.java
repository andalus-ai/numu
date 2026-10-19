package tech.kayys.numou.restaurant.kernel.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.restaurant.kernel.spi.DomainEventSink;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects published events; for tests and the dev profile. */
public final class RecordingEventSink implements DomainEventSink {

    public record Published(UUID tenantId, DomainEvent event) { }

    private final List<Published> published = new CopyOnWriteArrayList<>();

    @Override
    public Uni<Void> publish(UUID tenantId, List<DomainEvent> events) {
        events.forEach(e -> published.add(new Published(tenantId, e)));
        return Uni.createFrom().voidItem();
    }

    public List<Published> all() {
        return List.copyOf(published);
    }

    public List<String> types() {
        return published.stream().map(p -> p.event().eventType()).toList();
    }
}
