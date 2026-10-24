package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Metadata for one event atomically leased to a processing worker. */
public record InboundWorkItem(
        InboundEventKey key,
        String eventType,
        String payloadSha256,
        ChannelType channelType,
        Optional<String> branchId,
        String correlationId,
        Instant receivedAt,
        int attemptCount,
        Instant leaseUntil) {

    public InboundWorkItem {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payloadSha256, "payloadSha256 must not be null");
        Objects.requireNonNull(channelType, "channelType must not be null");
        branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
    }
}
