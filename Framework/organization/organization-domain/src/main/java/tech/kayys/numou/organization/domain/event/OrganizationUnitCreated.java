package tech.kayys.syirkah.organization.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.util.UUID;

public record OrganizationUnitCreated(
        UUID eventId,
        Instant occurredAt,
        OrganizationUnitId unitId,
        OrganizationId organizationId,
        OrganizationUnitId parentUnitId,
        String name,
        String code
) implements DomainEvent {
    public OrganizationUnitCreated(OrganizationUnitId unitId, OrganizationId organizationId,
                                   OrganizationUnitId parentUnitId, String name, String code) {
        this(UUID.randomUUID(), Instant.now(), unitId, organizationId, parentUnitId, name, code);
    }
    @Override
    public String eventType() {
        return "organization.unit.created";
    }
}
