package tech.kayys.syirkah.organization.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.organization.domain.OrganizationId;

import java.time.Instant;
import java.util.UUID;

public record OrganizationCreated(
        UUID eventId,
        Instant occurredAt,
        OrganizationId organizationId,
        TenantId tenantId,
        String name,
        String legalName,
        String registrationNumber
) implements DomainEvent {
    public OrganizationCreated(OrganizationId organizationId, TenantId tenantId,
                               String name, String legalName, String registrationNumber) {
        this(UUID.randomUUID(), Instant.now(), organizationId, tenantId, name, legalName, registrationNumber);
    }
    @Override
    public String eventType() {
        return "organization.created";
    }
}
