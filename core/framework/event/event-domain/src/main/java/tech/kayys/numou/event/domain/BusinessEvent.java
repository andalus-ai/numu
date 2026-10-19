package tech.kayys.numou.event.domain;

import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

/**
 * A business event published on the Numou ecosystem bus
 * (base01.md §7, §P1-10).
 *
 * <p>The distinction that matters:
 *
 * <pre>
 *   DomainEvent  - what one bounded context says happened inside itself
 *   BusinessEvent - what the ecosystem promises about that happening
 * </pre>
 *
 * <p>A domain event may stay private forever. A business event is a public
 * contract: it is versioned, tenant-scoped, correlateable, and consumable
 * by a participant that runs none of Numou's software.
 */
public interface BusinessEvent extends DomainEvent {

    /**
     * Dotted, stable event name, e.g. {@code logistics.shipment.delivered}.
     */
    String eventType();

    /** Contract version of this event type. */
    EventVersion eventVersion();
}
