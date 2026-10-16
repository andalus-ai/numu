package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Base sealed contract for all immutable financial domain events.
 */
public interface AccountingEvent extends DomainEvent {
    UUID eventId();
    Instant occurredAt();

    @Override
    default String eventType() {
        return getClass().getSimpleName();
    }

    TenantRef tenantId();
    LedgerId ledgerId();
    String correlationId();
    String causationId();
}
