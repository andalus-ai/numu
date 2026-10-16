package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.talent.event.TalentReviewFinalized;
import tech.kayys.numou.workforce.domain.talent.event.TalentReviewSubmitted;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class TalentReview extends AbstractAggregateRoot<TalentReviewId> {

    private final TenantId tenantId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final LocalDate reviewDate;
    private TalentReviewStatus status;
    private String summary;

    private TalentReview(
            TalentReviewId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate reviewDate
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.reviewDate = Objects.requireNonNull(reviewDate, "reviewDate must not be null");
        this.status = TalentReviewStatus.DRAFT;
    }

    public static TalentReview create(
            TalentReviewId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate reviewDate
    ) {
        return new TalentReview(id, tenantId, workerId, employmentId, reviewDate);
    }

    public void submit(String summary) {
        this.summary = summary;
        this.status = TalentReviewStatus.SUBMITTED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new TalentReviewSubmitted(getId(), workerId));
    }

    public void calibrate() {
        this.status = TalentReviewStatus.CALIBRATED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void finalizeReview() {
        this.status = TalentReviewStatus.FINALIZED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new TalentReviewFinalized(getId(), workerId));
    }

    public TenantId getTenantId() { return tenantId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public LocalDate getReviewDate() { return reviewDate; }
    public TalentReviewStatus getStatus() { return status; }
    public String getSummary() { return summary; }
}
