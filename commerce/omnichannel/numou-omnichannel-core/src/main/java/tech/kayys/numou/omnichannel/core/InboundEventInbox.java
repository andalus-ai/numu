package tech.kayys.numou.omnichannel.core;

/**
 * Port for durable, atomic inbound-event deduplication.
 * Implementations must atomically insert a new event under the scoped key, or
 * return the stored digest for an existing key. Concurrent calls for one key
 * must not both return ACCEPTED. This interface is not a persistence implementation.
 */
public interface InboundEventInbox {
    /**
     * @param providerId stable connector/provider identifier, not a display name
     * @param envelope validated event metadata and payload fingerprint
     */
    InboxClaimResult claim(String providerId, InboundEventEnvelope envelope);
}