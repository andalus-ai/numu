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
        Instant leaseUntil,
        Optional<String> leaseToken) {

    public InboundWorkItem {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payloadSha256, "payloadSha256 must not be null");
        Objects.requireNonNull(channelType, "channelType must not be null");
        branchId = Objects.requireNonNull(branchId, "branchId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
        Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        leaseToken = Objects.requireNonNull(leaseToken, "leaseToken must not be null");
        if (leaseToken.isPresent() && attemptCount < 1) {
            throw new IllegalArgumentException("a leased item must have a positive attemptCount");
        }
    }

    /** True only for an item actually claimed by a worker, not a dead-letter listing. */
    public boolean isLeased() {
        return leaseToken.isPresent();
    }
}
