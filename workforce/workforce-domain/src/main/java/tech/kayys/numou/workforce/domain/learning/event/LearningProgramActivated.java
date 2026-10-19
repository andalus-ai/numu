package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;

import java.time.Instant;
import java.util.UUID;

public record LearningProgramActivated(
        UUID eventId,
        Instant occurredAt,
        LearningProgramId id
) implements DomainEvent {
    public LearningProgramActivated(LearningProgramId id) {
        this(UUID.randomUUID(), Instant.now(), id);
    }
    @Override public String eventType() { return "workforce.learning.program.activated"; }
}
