package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.event.CompetencyAssessmentRecorded;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

public final class CompetencyAssessment extends AbstractAggregateRoot<CompetencyAssessmentId> {

    private final PerformanceCycleId cycleId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final CompetencyId competencyId;
    private CompetencyRating rating;
    private String comment;
    private final WorkerId assessorWorkerId;

    private CompetencyAssessment(
            CompetencyAssessmentId id,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            CompetencyId competencyId,
            CompetencyRating rating,
            String comment,
            WorkerId assessorWorkerId
    ) {
        super(id);
        this.cycleId = Objects.requireNonNull(cycleId, "cycleId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.competencyId = Objects.requireNonNull(competencyId, "competencyId must not be null");
        this.rating = Objects.requireNonNull(rating, "rating must not be null");
        this.comment = comment;
        this.assessorWorkerId = Objects.requireNonNull(assessorWorkerId, "assessorWorkerId must not be null");
    }

    public static CompetencyAssessment create(
            CompetencyAssessmentId id,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            CompetencyId competencyId,
            CompetencyRating rating,
            String comment,
            WorkerId assessorWorkerId
    ) {
        CompetencyAssessment ca = new CompetencyAssessment(id, cycleId, workerId, employmentId, competencyId, rating, comment, assessorWorkerId);
        ca.raise(new CompetencyAssessmentRecorded(id, workerId, competencyId, rating));
        return ca;
    }

    public void update(CompetencyRating newRating, String newComment) {
        this.rating = Objects.requireNonNull(newRating, "rating must not be null");
        this.comment = newComment;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new CompetencyAssessmentRecorded(getId(), workerId, competencyId, newRating));
    }

    public PerformanceCycleId getCycleId() { return cycleId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public CompetencyId getCompetencyId() { return competencyId; }
    public CompetencyRating getRating() { return rating; }
    public String getComment() { return comment; }
    public WorkerId getAssessorWorkerId() { return assessorWorkerId; }
}
