package tech.kayys.syirkah.tenancy.domain.tenant.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Raised when a new Tenant is registered in the PROVISIONING state. */
public record TenantCreated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        String code
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.tenant.created"; }
}
