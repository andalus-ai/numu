package tech.kayys.numou.workforce.domain.learning.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.learning.LearningCompletionId;
import tech.kayys.numou.workforce.domain.learning.LearningCompletionOutcome;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.time.Instant;
import java.util.UUID;

public record LearningCompleted(
        UUID eventId,
        Instant occurredAt,
        LearningCompletionId id,
        LearningEnrollmentId enrollmentId,
        LearningCompletionOutcome outcome
) implements DomainEvent {
    public LearningCompleted(LearningCompletionId id, LearningEnrollmentId enrollmentId, LearningCompletionOutcome outcome) {
        this(UUID.randomUUID(), Instant.now(), id, enrollmentId, outcome);
    }
    @Override public String eventType() { return "workforce.learning.completed"; }
}
