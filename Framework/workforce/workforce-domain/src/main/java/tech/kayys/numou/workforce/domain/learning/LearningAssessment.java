package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.learning.event.LearningAssessmentRecorded;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class LearningAssessment extends AbstractAggregateRoot<LearningAssessmentId> {

    private final LearningEnrollmentId enrollmentId;
    private final LearningAssessmentType type;
    private final BigDecimal score;
    private final boolean passed;
    private final Instant assessedAt;
    private final String assessorRef;

    private LearningAssessment(
            LearningAssessmentId id,
            LearningEnrollmentId enrollmentId,
            LearningAssessmentType type,
            BigDecimal score,
            boolean passed,
            Instant assessedAt,
            String assessorRef
    ) {
        super(id);
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.score = score;
        this.passed = passed;
        this.assessedAt = Objects.requireNonNull(assessedAt, "assessedAt must not be null");
        this.assessorRef = assessorRef;
    }

    public static LearningAssessment record(
            LearningAssessmentId id,
            LearningEnrollmentId enrollmentId,
            LearningAssessmentType type,
            BigDecimal score,
            boolean passed,
            Instant assessedAt,
            String assessorRef
    ) {
        LearningAssessment assessment = new LearningAssessment(id, enrollmentId, type, score, passed, assessedAt, assessorRef);
        assessment.raise(new LearningAssessmentRecorded(id, enrollmentId, passed));
        return assessment;
    }

    public LearningEnrollmentId getEnrollmentId() { return enrollmentId; }
    public LearningAssessmentType getType() { return type; }
    public BigDecimal getScore() { return score; }
    public boolean isPassed() { return passed; }
    public Instant getAssessedAt() { return assessedAt; }
    public String getAssessorRef() { return assessorRef; }
}
