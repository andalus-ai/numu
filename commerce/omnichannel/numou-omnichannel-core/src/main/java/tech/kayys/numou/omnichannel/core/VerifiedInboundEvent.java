package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Objects;

/** A verified event whose payload is referenced indirectly, never embedded. */
public record VerifiedInboundEvent(
        String providerId,
        InboundEventEnvelope envelope,
        InboundPayloadReference payloadReference,
        Instant verifiedAt) {
    public VerifiedInboundEvent {
        Objects.requireNonNull(providerId, "providerId must not be null");
        providerId = providerId.trim();
        if (providerId.isEmpty() || providerId.length() > 200) {
            throw new IllegalArgumentException("providerId must contain 1 to 200 characters");
        }
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(payloadReference, "payloadReference must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    }
}
