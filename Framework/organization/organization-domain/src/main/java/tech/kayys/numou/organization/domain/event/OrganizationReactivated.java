package tech.kayys.syirkah.organization.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.organization.domain.OrganizationId;

import java.time.Instant;
import java.util.UUID;

public record OrganizationReactivated(
        UUID eventId,
        Instant occurredAt,
        OrganizationId organizationId,
        TenantId tenantId
) implements DomainEvent {
    public OrganizationReactivated(OrganizationId organizationId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), organizationId, tenantId);
    }
    @Override
    public String eventType() {
        return "organization.reactivated";
    }
}
