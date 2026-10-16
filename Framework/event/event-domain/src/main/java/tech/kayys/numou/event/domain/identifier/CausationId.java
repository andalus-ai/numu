package tech.kayys.numou.event.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;

/**
 * The event (or command) that directly caused this event.
 *
 * <p>Correlation answers "same business journey"; causation answers
 * "which step produced this one". With both, a support engineer can
 * reconstruct the chain that produced a wrong-looking event.
 */
public record CausationId(String value) implements DomainId<String>, Serializable {

    public CausationId {
        value = requireText(value);
    }

    public static CausationId of(String value) {
        return new CausationId(value);
    }

    public static CausationId ofEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        return new CausationId(eventId.value().toString());
    }

    private static String requireText(String value) {
        Objects.requireNonNull(value, "CausationId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CausationId cannot be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "CausationId{" + value + "}";
    }
}
