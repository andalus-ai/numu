package tech.kayys.syirkah.tenancy.domain.tenant.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.ref.OrganizationRef;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Raised when the legal organization reference of a Tenant changes. */
public record TenantOrganizationChanged(
        UUID eventId,
        Instant occurredAt,
        TenantId tenantId,
        OrganizationRef organization
) implements DomainEvent {

    @Override public String eventType() { return "tenancy.tenant.organization_changed"; }
}
