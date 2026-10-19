package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.talent.SuccessionPlanId;

import java.time.Instant;
import java.util.UUID;

public record SuccessionPlanActivated(
        UUID eventId,
        Instant occurredAt,
        SuccessionPlanId id
) implements DomainEvent {
    public SuccessionPlanActivated(SuccessionPlanId id) {
        this(UUID.randomUUID(), Instant.now(), id);
    }
    @Override public String eventType() { return "workforce.talent.succession_plan.activated"; }
}
