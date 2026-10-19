package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.CompetencyAssessmentId;
import tech.kayys.numou.workforce.domain.performance.CompetencyId;
import tech.kayys.numou.workforce.domain.performance.CompetencyRating;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record CompetencyAssessmentRecorded(
        UUID eventId,
        Instant occurredAt,
        CompetencyAssessmentId id,
        WorkerId workerId,
        CompetencyId competencyId,
        CompetencyRating rating
) implements DomainEvent {
    public CompetencyAssessmentRecorded(CompetencyAssessmentId id, WorkerId workerId, CompetencyId competencyId, CompetencyRating rating) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, competencyId, rating);
    }
    @Override public String eventType() { return "workforce.performance.competency_assessment.recorded"; }
}
