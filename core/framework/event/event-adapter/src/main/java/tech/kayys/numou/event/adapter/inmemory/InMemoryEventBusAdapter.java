package tech.kayys.numou.event.adapter.inmemory;

import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * High-performance in-memory event bus adapter for Monolith and Standalone deployments.
 * Dispatches domain events directly within the same JVM process without broker serialization overhead.
 */
public class InMemoryEventBusAdapter implements EventPublisherPort, EventSubscriberPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventBusAdapter.class);

    private final Map<String, List<SubscriberRegistration>> subscribers = new ConcurrentHashMap<>();

    private record SubscriberRegistration(
            String id,
            String eventType,
            Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler
    ) {}

    @Override
    public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return Uni.createFrom().voidItem();
        }

        String eventType = envelope.eventType();
        List<SubscriberRegistration> matched = new ArrayList<>();

        // Exact match
        List<SubscriberRegistration> exact = subscribers.get(eventType);
        if (exact != null) {
            matched.addAll(exact);
        }

        // Wildcard match ("*")
        List<SubscriberRegistration> all = subscribers.get("*");
        if (all != null) {
            matched.addAll(all);
        }

        if (matched.isEmpty()) {
            log.trace("No in-memory subscriber for event type: {}", eventType);
            return Uni.createFrom().voidItem();
        }

        List<Uni<Void>> invocations = new ArrayList<>();
        for (SubscriberRegistration reg : matched) {
            try {
                invocations.add(reg.handler().apply(envelope));
            } catch (Exception e) {
                log.error("Error invoking in-memory handler {} for event {}", reg.id(), eventType, e);
            }
        }

        if (invocations.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Uni.combine().all().unis(invocations).discardItems();
    }

    @Override
    public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Uni<Void>> list = new ArrayList<>();
        for (EventEnvelope<? extends BusinessEvent> env : envelopes) {
            list.add(publish(env));
        }

        return Uni.combine().all().unis(list).discardItems();
    }

    @Override
    public SubscriptionHandle subscribe(String eventType, Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        String subId = UUID.randomUUID().toString();
        SubscriberRegistration reg = new SubscriberRegistration(subId, eventType, handler);

        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(reg);
        log.debug("Subscribed in-memory handler {} to {}", subId, eventType);

        return () -> subId;
    }

    @Override
    public void unsubscribe(SubscriptionHandle handle) {
        if (handle == null) return;
        String id = handle.id();
        for (List<SubscriberRegistration> list : subscribers.values()) {
            list.removeIf(r -> r.id().equals(id));
        }
    }

    public void clear() {
        subscribers.clear();
    }
}
