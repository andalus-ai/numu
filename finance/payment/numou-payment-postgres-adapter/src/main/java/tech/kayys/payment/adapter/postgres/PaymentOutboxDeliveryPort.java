package tech.kayys.payment.adapter.postgres;

/**
 * Host-provided delivery boundary for payment outbox events.
 *
 * <p>Implementations may publish to an in-process event bus or a remote broker.
 * Delivery is at-least-once: consumers must deduplicate by eventId. This port
 * must not be wired directly to an unverified payment-provider callback.</p>
 */
@FunctionalInterface
public interface PaymentOutboxDeliveryPort {
    void deliver(PaymentOutboxRelayStore.ClaimedEvent event) throws Exception;
}
