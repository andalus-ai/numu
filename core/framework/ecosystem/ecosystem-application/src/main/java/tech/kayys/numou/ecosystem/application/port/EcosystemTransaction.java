package tech.kayys.numou.ecosystem.application.port;

import tech.kayys.numou.foundation.application.event.EventPublisher;

/**
 * Transaction-scoped persistence and event capabilities for one Ecosystem command.
 *
 * <p>Every repository and the event publisher exposed by this context must be
 * bound to the same physical database transaction. Implementations must not
 * return pool-backed repositories or publish directly to a broker from this
 * context. Events are appended to a durable outbox before the transaction
 * commits; asynchronous broker delivery happens after commit.</p>
 */
public interface EcosystemTransaction {
    PartyRepository parties();
    OrganizationRepository organizations();
    EventPublisher events();
}
