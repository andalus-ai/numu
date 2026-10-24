package tech.kayys.numou.omnichannel.core;

/**
 * Application integration boundary for a verified inbound event.
 *
 * <p>Implementations map provider-neutral event types to existing Numou
 * application use cases. They must not write another module's tables directly,
 * and must make downstream effects idempotent using the scoped event identity
 * or the target use case's idempotency mechanism.</p>
 */
public interface InboundEventDispatcher {

    /** Dispatches a verified event to existing application ports/use cases. */
    void dispatch(VerifiedInboundEvent event);
}
