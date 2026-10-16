package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.learning.event.LearningCompleted;

import java.time.Instant;
import java.util.Objects;

public final class LearningCompletion extends AbstractAggregateRoot<LearningCompletionId> {

    private final LearningEnrollmentId enrollmentId;
    private final Instant completedAt;
    private final LearningCompletionOutcome outcome;
    private final String resultSummary;

    private LearningCompletion(
            LearningCompletionId id,
            LearningEnrollmentId enrollmentId,
            Instant completedAt,
            LearningCompletionOutcome outcome,
            String resultSummary
    ) {
        super(id);
        this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId must not be null");
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.resultSummary = resultSummary;
    }

    public static LearningCompletion record(
            LearningCompletionId id,
            LearningEnrollmentId enrollmentId,
            Instant completedAt,
            LearningCompletionOutcome outcome,
            String resultSummary
    ) {
        LearningCompletion completion = new LearningCompletion(id, enrollmentId, completedAt, outcome, resultSummary);
        completion.raise(new LearningCompleted(id, enrollmentId, outcome));
        return completion;
    }

    public LearningEnrollmentId getEnrollmentId() { return enrollmentId; }
    public Instant getCompletedAt() { return completedAt; }
    public LearningCompletionOutcome getOutcome() { return outcome; }
    public String getResultSummary() { return resultSummary; }
}
