package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider-neutral metadata for an inbound event after transport parsing.
 *
 * <p>The payload itself is intentionally not stored here. A durable inbox
 * adapter should persist payload bytes separately under an explicit retention
 * and redaction policy. The SHA-256 fingerprint allows duplicate/conflict
 * detection without copying sensitive payload into logs or metadata.</p>
 */
public record InboundEventEnvelope(
        ChannelContext context,
        String externalEventId,
        String eventType,
        String payloadSha256,
        Instant receivedAt,
        Optional<Instant> providerOccurredAt) {

    public InboundEventEnvelope {
        Objects.requireNonNull(context, "context must not be null");
        externalEventId = required(externalEventId, "externalEventId", 250);
        eventType = required(eventType, "eventType", 200);
        payloadSha256 = required(payloadSha256, "payloadSha256", 64).toLowerCase();
        if (!payloadSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadSha256 must be a 64-character SHA-256 hex digest");
        }
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(providerOccurredAt, "providerOccurredAt must not be null");
    }

    private static String required(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
