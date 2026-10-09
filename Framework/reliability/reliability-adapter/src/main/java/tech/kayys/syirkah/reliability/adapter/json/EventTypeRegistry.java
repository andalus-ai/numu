package tech.kayys.syirkah.reliability.adapter.json;

import tech.kayys.syirkah.event.domain.BusinessEvent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a dotted event type to the payload class used to deserialise it. Each bounded
 * context registers its public business events at startup.
 */
public final class EventTypeRegistry {

    private final Map<String, Class<? extends BusinessEvent>> types = new ConcurrentHashMap<>();

    public EventTypeRegistry register(String eventType, Class<? extends BusinessEvent> payloadClass) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payloadClass, "payloadClass cannot be null");
        final var previous = types.putIfAbsent(eventType, payloadClass);
        if (previous != null && !previous.equals(payloadClass)) {
            throw new IllegalStateException("Event type already registered to a different class: " + eventType);
        }
        return this;
    }

    private volatile Class<? extends BusinessEvent> fallback;

    /** Class used for any event type without an explicit registration (e.g. {@link JsonDomainEvent}). */
    public EventTypeRegistry registerFallback(Class<? extends BusinessEvent> payloadClass) {
        this.fallback = Objects.requireNonNull(payloadClass, "payloadClass cannot be null");
        return this;
    }

    public Class<? extends BusinessEvent> classFor(String eventType) {
        final var c = types.get(eventType) != null ? types.get(eventType) : fallback;
        if (c == null) {
            // IllegalArgumentException => classified PERMANENT by the relay: retrying cannot fix an unknown type.
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        return c;
    }
}
