package tech.kayys.numou.organization.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.util.UUID;

public record OrganizationUnitDissolved(
        UUID eventId,
        Instant occurredAt,
        OrganizationUnitId unitId,
        OrganizationId organizationId
) implements DomainEvent {
    public OrganizationUnitDissolved(OrganizationUnitId unitId, OrganizationId organizationId) {
        this(UUID.randomUUID(), Instant.now(), unitId, organizationId);
    }
    @Override
    public String eventType() {
        return "organization.unit.dissolved";
    }
}
