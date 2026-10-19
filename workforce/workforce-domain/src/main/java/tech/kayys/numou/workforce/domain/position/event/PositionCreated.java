package tech.kayys.numou.workforce.domain.position.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.time.Instant;
import java.util.UUID;

public record PositionCreated(
        UUID eventId,
        Instant occurredAt,
        PositionId positionId,
        OrganizationRef organization,
        String code,
        String title
) implements DomainEvent {

    public PositionCreated(PositionId positionId, OrganizationRef organization, String code, String title) {
        this(UUID.randomUUID(), Instant.now(), positionId, organization, code, title);
    }

    @Override
    public String eventType() {
        return "workforce.position.created";
    }
}
