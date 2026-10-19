package tech.kayys.numou.workforce.domain.socialinsurance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.socialinsurance.event.WorkerSocialInsuranceEnded;
import tech.kayys.numou.workforce.domain.socialinsurance.event.WorkerSocialInsuranceEnrolled;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * WorkerSocialInsuranceEnrollment aggregate root — records a worker's participation in a social insurance scheme.
 *
 * <p>Enforces membershipNumber, effective dating, and status lifecycle.
 */
public final class WorkerSocialInsuranceEnrollment extends AbstractAggregateRoot<WorkerSocialInsuranceEnrollmentId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final SocialInsuranceSchemeId schemeId;
    private final String membershipNumber;
    private final LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private WorkerSocialInsuranceStatus status;

    private WorkerSocialInsuranceEnrollment(
            WorkerSocialInsuranceEnrollmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            SocialInsuranceSchemeId schemeId,
            String membershipNumber,
            LocalDate effectiveFrom
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.schemeId = Objects.requireNonNull(schemeId, "schemeId must not be null");
        this.membershipNumber = Objects.requireNonNull(membershipNumber, "membershipNumber must not be null");
        if (membershipNumber.isBlank()) throw new IllegalArgumentException("membershipNumber must not be blank");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.status = WorkerSocialInsuranceStatus.ACTIVE;
    }

    public static WorkerSocialInsuranceEnrollment create(
            WorkerSocialInsuranceEnrollmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            SocialInsuranceSchemeId schemeId,
            String membershipNumber,
            LocalDate effectiveFrom
    ) {
        WorkerSocialInsuranceEnrollment enrollment = new WorkerSocialInsuranceEnrollment(
                id, workerId, employmentId, schemeId, membershipNumber, effectiveFrom);
        enrollment.raise(new WorkerSocialInsuranceEnrolled(id, workerId, employmentId, schemeId, membershipNumber, effectiveFrom));
        return enrollment;
    }

    public void end(LocalDate effectiveTo) {
        Objects.requireNonNull(effectiveTo, "effectiveTo must not be null");
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot precede effectiveFrom");
        }
        this.effectiveTo = effectiveTo;
        this.status = WorkerSocialInsuranceStatus.ENDED;
        raise(new WorkerSocialInsuranceEnded(getId(), workerId, schemeId, effectiveTo));
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (status != WorkerSocialInsuranceStatus.ACTIVE) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }

    public WorkerId workerId() { return workerId; }
    public EmploymentId employmentId() { return employmentId; }
    public SocialInsuranceSchemeId schemeId() { return schemeId; }
    public String membershipNumber() { return membershipNumber; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public WorkerSocialInsuranceStatus status() { return status; }
}
