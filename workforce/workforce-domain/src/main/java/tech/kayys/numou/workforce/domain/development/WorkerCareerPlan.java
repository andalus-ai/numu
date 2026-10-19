package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.development.event.WorkerCareerPlanCreated;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Aggregate root representing a worker's individual Career Plan.
 *
 * <p>A {@code WorkerCareerPlan} maps a worker to an optional {@link CareerPath} and
 * captures their current position, desired target position, and the date by which
 * they aim to reach that target. It provides the high-level career aspiration
 * canvas that is supplemented by detailed {@link DevelopmentPlan}s.</p>
 *
 * <p>Lifecycle: {@code DRAFT → ACTIVE → COMPLETED}; any non-terminal state can
 * transition to {@code CANCELLED}.</p>
 */
public class WorkerCareerPlan extends AbstractAggregateRoot<WorkerCareerPlanId> {

    private final WorkerId workerId;
    private final EmploymentId employmentId;

    /** Optional: worker may not be assigned to a formal career path. */
    private CareerPathId careerPathId;

    /** Optional: the position the worker currently holds. */
    private PositionId currentPositionId;

    /** Optional: the position the worker aspires to reach. */
    private PositionId targetPositionId;

    /** The date by which the worker aims to reach the target position. */
    private LocalDate targetDate;

    private WorkerCareerPlanStatus status;
    private final Instant createdAt;

    // -------------------------------------------------------------------------
    // Private constructor – use factory method
    // -------------------------------------------------------------------------

    private WorkerCareerPlan(
            WorkerCareerPlanId id,
            WorkerId workerId,
            EmploymentId employmentId,
            CareerPathId careerPathId,
            PositionId currentPositionId,
            PositionId targetPositionId,
            LocalDate targetDate,
            WorkerCareerPlanStatus status,
            Instant createdAt) {
        super(id);
        this.workerId = workerId;
        this.employmentId = employmentId;
        this.careerPathId = careerPathId;
        this.currentPositionId = currentPositionId;
        this.targetPositionId = targetPositionId;
        this.targetDate = targetDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code WorkerCareerPlan} in {@code DRAFT} status and registers
     * the {@link WorkerCareerPlanCreated} domain event.
     *
     * @param id                the unique identity.
     * @param workerId          the worker this plan belongs to.
     * @param employmentId      the active employment record.
     * @param careerPathId      optional reference to a formal {@link CareerPath}.
     * @param currentPositionId optional current position of the worker.
     * @param targetPositionId  optional aspirational target position.
     * @param targetDate        optional target date for reaching the target position.
     */
    public static WorkerCareerPlan create(
            WorkerCareerPlanId id,
            WorkerId workerId,
            EmploymentId employmentId,
            CareerPathId careerPathId,
            PositionId currentPositionId,
            PositionId targetPositionId,
            LocalDate targetDate) {

        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (workerId == null) throw new IllegalArgumentException("workerId must not be null");
        if (employmentId == null) throw new IllegalArgumentException("employmentId must not be null");

        WorkerCareerPlan plan = new WorkerCareerPlan(
                id, workerId, employmentId,
                careerPathId, currentPositionId, targetPositionId, targetDate,
                WorkerCareerPlanStatus.DRAFT, Instant.now());

        plan.registerEvent(new WorkerCareerPlanCreated(id, workerId));
        return plan;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Transitions the plan from {@code DRAFT} to {@code ACTIVE}.
     *
     * @throws IllegalStateException if the plan is not in {@code DRAFT} status.
     */
    public void activate() {
        if (status != WorkerCareerPlanStatus.DRAFT) {
            throw new IllegalStateException(
                    "WorkerCareerPlan can only be activated from DRAFT status; current=" + status);
        }
        this.status = WorkerCareerPlanStatus.ACTIVE;
    }

    /**
     * Sets or updates the worker's target position and desired achievement date.
     * Permitted in {@code DRAFT} or {@code ACTIVE} status.
     *
     * @param targetPositionId the desired target position.
     * @param targetDate       the date by which the worker wants to reach the position.
     * @throws IllegalStateException if the plan is in a terminal state.
     */
    public void setTargetPosition(PositionId targetPositionId, LocalDate targetDate) {
        if (status == WorkerCareerPlanStatus.COMPLETED || status == WorkerCareerPlanStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot update target position on a plan with terminal status=" + status);
        }
        if (targetPositionId == null) throw new IllegalArgumentException("targetPositionId must not be null");
        this.targetPositionId = targetPositionId;
        this.targetDate = targetDate;
    }

    /**
     * Transitions the plan from {@code ACTIVE} to {@code COMPLETED}.
     *
     * @throws IllegalStateException if the plan is not in {@code ACTIVE} status.
     */
    public void complete() {
        if (status != WorkerCareerPlanStatus.ACTIVE) {
            throw new IllegalStateException(
                    "WorkerCareerPlan can only be completed from ACTIVE status; current=" + status);
        }
        this.status = WorkerCareerPlanStatus.COMPLETED;
    }

    /**
     * Cancels the plan from any non-terminal state.
     *
     * @throws IllegalStateException if the plan is already {@code COMPLETED} or {@code CANCELLED}.
     */
    public void cancel() {
        if (status == WorkerCareerPlanStatus.COMPLETED || status == WorkerCareerPlanStatus.CANCELLED) {
            throw new IllegalStateException(
                    "WorkerCareerPlan cannot be cancelled from terminal status=" + status);
        }
        this.status = WorkerCareerPlanStatus.CANCELLED;
    }

    /** Assigns or reassigns the career path for this plan. */
    public void assignCareerPath(CareerPathId careerPathId) {
        if (status == WorkerCareerPlanStatus.COMPLETED || status == WorkerCareerPlanStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot assign career path on a plan with terminal status=" + status);
        }
        this.careerPathId = careerPathId;
    }

    /** Updates the worker's current position. */
    public void updateCurrentPosition(PositionId currentPositionId) {
        if (status == WorkerCareerPlanStatus.COMPLETED || status == WorkerCareerPlanStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot update current position on a plan with terminal status=" + status);
        }
        this.currentPositionId = currentPositionId;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public WorkerId getWorkerId() { return workerId; }

    public EmploymentId getEmploymentId() { return employmentId; }

    /** May be {@code null} if no formal career path has been assigned. */
    public CareerPathId getCareerPathId() { return careerPathId; }

    /** May be {@code null} if the current position has not been specified. */
    public PositionId getCurrentPositionId() { return currentPositionId; }

    /** May be {@code null} if no target position has been set yet. */
    public PositionId getTargetPositionId() { return targetPositionId; }

    /** May be {@code null} if no target date has been set yet. */
    public LocalDate getTargetDate() { return targetDate; }

    public WorkerCareerPlanStatus getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }
}
