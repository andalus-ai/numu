package tech.kayys.numou.workforce.domain.talent.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.talent.ReadinessHorizon;
import tech.kayys.numou.workforce.domain.talent.ReadinessLevel;
import tech.kayys.numou.workforce.domain.talent.SuccessionCandidateId;
import tech.kayys.numou.workforce.domain.talent.SuccessionReadinessAssessmentId;

import java.time.Instant;
import java.util.UUID;

public record SuccessionCandidateAssessed(
        UUID eventId,
        Instant occurredAt,
        SuccessionReadinessAssessmentId id,
        SuccessionCandidateId candidateId,
        ReadinessLevel readiness,
        ReadinessHorizon horizon
) implements DomainEvent {
    public SuccessionCandidateAssessed(SuccessionReadinessAssessmentId id, SuccessionCandidateId candidateId, ReadinessLevel readiness, ReadinessHorizon horizon) {
        this(UUID.randomUUID(), Instant.now(), id, candidateId, readiness, horizon);
    }
    @Override public String eventType() { return "workforce.talent.candidate.assessed"; }
}
