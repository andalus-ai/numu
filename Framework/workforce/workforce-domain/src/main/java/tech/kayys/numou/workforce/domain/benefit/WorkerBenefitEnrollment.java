package tech.kayys.numou.workforce.domain.benefit;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.benefit.event.WorkerBenefitEnded;
import tech.kayys.numou.workforce.domain.benefit.event.WorkerBenefitEnrolled;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * WorkerBenefitEnrollment aggregate root — associates a Worker (and optionally an Employment) with a Benefit.
 *
 * <p>Always effective-dated. Never overwrites historical enrollment.
 */
public final class WorkerBenefitEnrollment extends AbstractAggregateRoot<WorkerBenefitEnrollmentId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId; // optional
    private final BenefitId benefitId;
    private final LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private WorkerBenefitEnrollmentStatus status;

    private WorkerBenefitEnrollment(
            WorkerBenefitEnrollmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            BenefitId benefitId,
            LocalDate effectiveFrom
    ) {
        super(id);
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = employmentId; // optional
        this.benefitId = Objects.requireNonNull(benefitId, "benefitId must not be null");
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.status = WorkerBenefitEnrollmentStatus.ACTIVE;
    }

    public static WorkerBenefitEnrollment create(
            WorkerBenefitEnrollmentId id,
            WorkerId workerId,
            EmploymentId employmentId,
            BenefitId benefitId,
            LocalDate effectiveFrom
    ) {
        WorkerBenefitEnrollment enrollment = new WorkerBenefitEnrollment(
                id, workerId, employmentId, benefitId, effectiveFrom);
        enrollment.raise(new WorkerBenefitEnrolled(id, workerId, employmentId, benefitId, effectiveFrom));
        return enrollment;
    }

    public void end(LocalDate effectiveTo) {
        Objects.requireNonNull(effectiveTo, "effectiveTo must not be null");
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot precede effectiveFrom");
        }
        this.effectiveTo = effectiveTo;
        this.status = WorkerBenefitEnrollmentStatus.ENDED;
        raise(new WorkerBenefitEnded(getId(), workerId, benefitId, effectiveTo));
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        if (status != WorkerBenefitEnrollmentStatus.ACTIVE) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }

    public WorkerId workerId() { return workerId; }
    public EmploymentId employmentId() { return employmentId; }
    public BenefitId benefitId() { return benefitId; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public WorkerBenefitEnrollmentStatus status() { return status; }
}
