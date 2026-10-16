package tech.kayys.numou.foundation.application.context;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Distributed tracing correlation identifier, tracking a business operation across services.
 */
public record CorrelationId(String value) implements DomainId<String> {

    public CorrelationId {
        Objects.requireNonNull(value, "CorrelationId value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CorrelationId value cannot be blank");
        }
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID().toString());
    }

    public static CorrelationId of(String value) {
        return new CorrelationId(value);
    }
}
