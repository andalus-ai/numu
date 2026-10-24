package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Operator-facing summary. Deliberately excludes raw webhook payload and secrets. */
public record DeadLetterEvent(
        InboundEventKey key,
        String eventType,
        String errorCode,
        int attemptCount,
        Instant receivedAt,
        Instant updatedAt,
        Optional<String> correlationId) {

    public DeadLetterEvent {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
