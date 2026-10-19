package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceReviewApproved;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceReviewCreated;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceReviewFinalized;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceReviewRejected;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceReviewSubmitted;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class PerformanceReview extends AbstractAggregateRoot<PerformanceReviewId> {

    private final PerformanceCycleId cycleId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final WorkerId reviewerWorkerId;
    private PerformanceReviewStatus status;
    private String summary;
    private BigDecimal overallScore;

    private PerformanceReview(
            PerformanceReviewId id,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            WorkerId reviewerWorkerId
    ) {
        super(id);
        this.cycleId = Objects.requireNonNull(cycleId, "cycleId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.reviewerWorkerId = Objects.requireNonNull(reviewerWorkerId, "reviewerWorkerId must not be null");
        this.status = PerformanceReviewStatus.DRAFT;
    }

    public static PerformanceReview create(
            PerformanceReviewId id,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            WorkerId reviewerWorkerId
    ) {
        PerformanceReview review = new PerformanceReview(id, cycleId, workerId, employmentId, reviewerWorkerId);
        review.raise(new PerformanceReviewCreated(id, workerId, cycleId));
        return review;
    }

    public void submit(String summary) {
        if (status != PerformanceReviewStatus.DRAFT) {
            throw new IllegalStateException("Only draft reviews can be submitted");
        }
        this.summary = summary;
        this.status = PerformanceReviewStatus.SUBMITTED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceReviewSubmitted(getId(), workerId));
    }

    public void acknowledge() {
        if (status != PerformanceReviewStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted reviews can be acknowledged");
        }
        this.status = PerformanceReviewStatus.ACKNOWLEDGED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void approve() {
        if (status != PerformanceReviewStatus.ACKNOWLEDGED && status != PerformanceReviewStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted or acknowledged reviews can be approved");
        }
        this.status = PerformanceReviewStatus.APPROVED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceReviewApproved(getId(), workerId));
    }

    public void reject(String reason) {
        if (status != PerformanceReviewStatus.SUBMITTED && status != PerformanceReviewStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Cannot reject review in status: " + status);
        }
        this.status = PerformanceReviewStatus.REJECTED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceReviewRejected(getId(), reason));
    }

    public void finalizeReview(BigDecimal score) {
        if (status != PerformanceReviewStatus.APPROVED) {
            throw new IllegalStateException("Only approved reviews can be finalized");
        }
        this.overallScore = Objects.requireNonNull(score, "score must not be null");
        this.status = PerformanceReviewStatus.FINALIZED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceReviewFinalized(getId(), workerId, score));
    }

    public PerformanceCycleId getCycleId() { return cycleId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public WorkerId getReviewerWorkerId() { return reviewerWorkerId; }
    public PerformanceReviewStatus getStatus() { return status; }
    public String getSummary() { return summary; }
    public BigDecimal getOverallScore() { return overallScore; }
}
