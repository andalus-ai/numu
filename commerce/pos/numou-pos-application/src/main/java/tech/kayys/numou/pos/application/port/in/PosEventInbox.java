package tech.kayys.numou.pos.application.port.in;

import java.util.UUID;

/**
 * Executes a consumer's business effect at most once per tenant, consumer and event.
 * The implementation must commit the inbox marker and handler's business writes
 * in the same transaction.
 */
@FunctionalInterface
public interface PosEventInbox {
    /** @return true when the handler ran; false when this event was already handled. */
    boolean executeOnce(UUID tenantId, String consumerName, UUID eventId, Runnable handler);
}
