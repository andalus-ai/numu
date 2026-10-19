package tech.kayys.numou.foundation.application.context;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Causation identifier, tracking the immediate cause (e.g. parent command or event) of an operation.
 */
public record CausationId(String value) implements DomainId<String> {

    public CausationId {
        Objects.requireNonNull(value, "CausationId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CausationId value cannot be blank");
        }
    }

    public static CausationId generate() {
        return new CausationId(UUID.randomUUID().toString());
    }

    public static CausationId of(String value) {
        return new CausationId(value);
    }
}
