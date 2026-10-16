package tech.kayys.numou.organization.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

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
