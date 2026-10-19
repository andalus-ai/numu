package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.learning.event.LearningEnrolled;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.Objects;

public final class LearningEnrollment extends AbstractAggregateRoot<LearningEnrollmentId> {

    private final TenantId tenantId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final LearningOfferingId offeringId;
    private final Instant enrolledAt;
    private LearningEnrollmentStatus status;

    private LearningEnrollment(
            LearningEnrollmentId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LearningOfferingId offeringId,
            Instant enrolledAt
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.offeringId = Objects.requireNonNull(offeringId, "offeringId must not be null");
        this.enrolledAt = Objects.requireNonNull(enrolledAt, "enrolledAt must not be null");
        this.status = LearningEnrollmentStatus.ENROLLED;
    }

    public static LearningEnrollment create(
            LearningEnrollmentId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LearningOfferingId offeringId,
            Instant enrolledAt
    ) {
        LearningEnrollment enrollment = new LearningEnrollment(id, tenantId, workerId, employmentId, offeringId, enrolledAt);
        enrollment.raise(new LearningEnrolled(id, workerId, offeringId));
        return enrollment;
    }

    public void start() {
        this.status = LearningEnrollmentStatus.IN_PROGRESS;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = LearningEnrollmentStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void fail() {
        this.status = LearningEnrollmentStatus.FAILED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void withdraw() {
        this.status = LearningEnrollmentStatus.WITHDRAWN;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public LearningOfferingId getOfferingId() { return offeringId; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public LearningEnrollmentStatus getStatus() { return status; }
}
