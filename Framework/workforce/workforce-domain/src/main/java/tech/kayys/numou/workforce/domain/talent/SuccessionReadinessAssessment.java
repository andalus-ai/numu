package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.talent.event.SuccessionCandidateAssessed;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

public final class SuccessionReadinessAssessment extends AbstractAggregateRoot<SuccessionReadinessAssessmentId> {

    private final SuccessionCandidateId candidateId;
    private final Instant assessedAt;
    private final WorkerId assessorWorkerId;
    private final ReadinessLevel readiness;
    private final ReadinessHorizon horizon;
    private final String rationale;

    private SuccessionReadinessAssessment(
            SuccessionReadinessAssessmentId id,
            SuccessionCandidateId candidateId,
            Instant assessedAt,
            WorkerId assessorWorkerId,
            ReadinessLevel readiness,
            ReadinessHorizon horizon,
            String rationale
    ) {
        super(id);
        this.candidateId = Objects.requireNonNull(candidateId, "candidateId must not be null");
        this.assessedAt = Objects.requireNonNull(assessedAt, "assessedAt must not be null");
        this.assessorWorkerId = Objects.requireNonNull(assessorWorkerId, "assessorWorkerId must not be null");
        this.readiness = Objects.requireNonNull(readiness, "readiness must not be null");
        this.horizon = Objects.requireNonNull(horizon, "horizon must not be null");
        this.rationale = rationale;
    }

    public static SuccessionReadinessAssessment assess(
            SuccessionReadinessAssessmentId id,
            SuccessionCandidateId candidateId,
            Instant assessedAt,
            WorkerId assessorWorkerId,
            ReadinessLevel readiness,
            ReadinessHorizon horizon,
            String rationale
    ) {
        SuccessionReadinessAssessment assessment = new SuccessionReadinessAssessment(id, candidateId, assessedAt, assessorWorkerId, readiness, horizon, rationale);
        assessment.raise(new SuccessionCandidateAssessed(id, candidateId, readiness, horizon));
        return assessment;
    }

    public SuccessionCandidateId getCandidateId() { return candidateId; }
    public Instant getAssessedAt() { return assessedAt; }
    public WorkerId getAssessorWorkerId() { return assessorWorkerId; }
    public ReadinessLevel getReadiness() { return readiness; }
    public ReadinessHorizon getHorizon() { return horizon; }
    public String getRationale() { return rationale; }
}
