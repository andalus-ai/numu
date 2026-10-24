package tech.kayys.numou.omnichannel.core;

/**
 * Port for durable, atomic inbound event deduplication.
 *
 * <p>Implementations must atomically insert a new event under the scoped key,
 * or return the persisted payload digest for an existing key. Concurrent calls
 * for the same key must not both return ACCEPTED. This interface does not
 * prescribe a database or broker and must be implemented in an infrastructure
 * adapter before the webhook path is considered production-ready.</p>
 */
public interface InboundEventInbox {

    /**
     * Atomically claims an event for processing.
     *
     * @param providerId stable connector/provider identifier, not a display name
     * @param envelope validated metadata envelope with payload fingerprint
     * @return ACCEPTED for the sole successful claimant, DUPLICATE for the same
     *         payload replay, or PAYLOAD_CONFLICT when the key is reused with
     *         different content
     */
    InboxClaimResult claim(String providerId, InboundEventEnvelope envelope);
}
