package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Durable metadata needed to reconstruct a verified event for its current lease. */
public record InboundVerifiedEventMetadata(
        InboundPayloadReference payloadReference,
        Instant verifiedAt,
        Optional<Instant> providerOccurredAt) {
    public InboundVerifiedEventMetadata {
        Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        providerOccurredAt = Objects.requireNonNull(providerOccurredAt, "providerOccurredAt must not be null");
    }
}
