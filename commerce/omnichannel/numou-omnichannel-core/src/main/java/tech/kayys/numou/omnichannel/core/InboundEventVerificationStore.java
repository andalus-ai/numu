package tech.kayys.numou.omnichannel.core;

import java.time.Instant;

/**
 * Durable trust-boundary port. A record becomes claimable only after its
 * payload reference is persisted and its stored digest matches the verified
 * raw request bytes.
 */
public interface InboundEventVerificationStore {

    /**
     * Atomically attaches the opaque payload reference and transitions RECEIVED
     * to VERIFIED only when the stored payload digest matches expectedSha256.
     * Repeated calls with the same digest and reference are idempotent.
     */
    InboundVerificationResult markVerified(
            InboundEventKey key,
            String expectedSha256,
            InboundPayloadReference payloadReference,
            Instant verifiedAt);
}
