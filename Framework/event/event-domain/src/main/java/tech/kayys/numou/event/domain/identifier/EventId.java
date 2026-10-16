package tech.kayys.numou.event.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Stable identity of a single business event occurrence.
 *
 * <p>Consumers use this to deduplicate redelivery (see the inbox / idempotency
 * requirement in base01.md §P1-15): the same EventId arriving twice must be
 * processed once.
 */
public record EventId(UUID value) implements DomainId<UUID>, Serializable {

    public EventId {
        Objects.requireNonNull(value, "EventId value cannot be null");
    }

    public static EventId of(UUID value) {
        return new EventId(value);
    }

    public static EventId generate() {
        return new EventId(UUID.randomUUID());
    }

    public static EventId fromString(String value) {
        return new EventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "EventId{" + value + "}";
    }
}
