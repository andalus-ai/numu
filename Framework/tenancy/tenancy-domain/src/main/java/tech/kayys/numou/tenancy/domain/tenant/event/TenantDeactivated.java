package tech.kayys.syirkah.tenancy.domain.tenant.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Raised when a Tenant is permanently deactivated. */
public record TenantDeactivated(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.tenant.deactivated"; }
}
