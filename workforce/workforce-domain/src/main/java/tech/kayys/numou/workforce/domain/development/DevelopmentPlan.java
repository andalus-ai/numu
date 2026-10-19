package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.development.event.DevelopmentPlanCompleted;
import tech.kayys.numou.workforce.domain.development.event.DevelopmentPlanCreated;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a worker's annual Development Plan.
 *
 * <p>A development plan groups a set of identified {@link DevelopmentNeed}s for
 * a given worker and review cycle year. Only references (IDs) to needs are stored
 * inside this aggregate; need data lives in {@link DevelopmentNeed}.</p>
 *
 * <p>Lifecycle: {@code DRAFT → ACTIVE → COMPLETED}; any non-terminal state can
 * transition to {@code CANCELLED}.</p>
 */
public class DevelopmentPlan extends AbstractAggregateRoot<DevelopmentPlanId> {

    private final TenantId tenantId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final int cycleYear;
    private final Instant createdAt;

    private DevelopmentPlanStatus status;
    private final List<DevelopmentNeedId> needIds;

    // -------------------------------------------------------------------------
    // Private constructor – use factory method
    // -------------------------------------------------------------------------

    private DevelopmentPlan(
            DevelopmentPlanId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            int cycleYear,
            DevelopmentPlanStatus status,
            Instant createdAt,
            List<DevelopmentNeedId> needIds) {
        super(id);
        this.tenantId = tenantId;
        this.workerId = workerId;
        this.employmentId = employmentId;
        this.cycleYear = cycleYear;
        this.status = status;
        this.createdAt = createdAt;
        this.needIds = new ArrayList<>(needIds);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code DevelopmentPlan} in {@code DRAFT} status and registers
     * the {@link DevelopmentPlanCreated} domain event.
     */
    public static DevelopmentPlan create(
            DevelopmentPlanId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            int cycleYear) {

        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId must not be null");
        if (workerId == null) throw new IllegalArgumentException("workerId must not be null");
        if (employmentId == null) throw new IllegalArgumentException("employmentId must not be null");
        if (cycleYear < 2000 || cycleYear > 2100)
            throw new IllegalArgumentException("cycleYear must be between 2000 and 2100");

        DevelopmentPlan plan = new DevelopmentPlan(
                id, tenantId, workerId, employmentId, cycleYear,
                DevelopmentPlanStatus.DRAFT, Instant.now(), List.of());

        plan.registerEvent(new DevelopmentPlanCreated(id, workerId, cycleYear));
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
        if (status != DevelopmentPlanStatus.DRAFT) {
            throw new IllegalStateException(
                    "DevelopmentPlan can only be activated from DRAFT status; current=" + status);
        }
        this.status = DevelopmentPlanStatus.ACTIVE;
    }

    /**
     * Transitions the plan from {@code ACTIVE} to {@code COMPLETED} and registers
     * the {@link DevelopmentPlanCompleted} event.
     *
     * @throws IllegalStateException if the plan is not in {@code ACTIVE} status.
     */
    public void complete() {
        if (status != DevelopmentPlanStatus.ACTIVE) {
            throw new IllegalStateException(
                    "DevelopmentPlan can only be completed from ACTIVE status; current=" + status);
        }
        this.status = DevelopmentPlanStatus.COMPLETED;
        registerEvent(new DevelopmentPlanCompleted(getId(), workerId));
    }

    /**
     * Cancels the plan from any non-terminal state.
     *
     * @throws IllegalStateException if the plan is already {@code COMPLETED} or {@code CANCELLED}.
     */
    public void cancel() {
        if (status == DevelopmentPlanStatus.COMPLETED || status == DevelopmentPlanStatus.CANCELLED) {
            throw new IllegalStateException(
                    "DevelopmentPlan cannot be cancelled from terminal status=" + status);
        }
        this.status = DevelopmentPlanStatus.CANCELLED;
    }

    /**
     * Adds a reference to a {@link DevelopmentNeed} to this plan.
     * Only permitted when the plan is in {@code DRAFT} or {@code ACTIVE} status.
     *
     * @param needId the id of the need to associate with this plan.
     * @throws IllegalStateException    if the plan is not in DRAFT or ACTIVE status.
     * @throws IllegalArgumentException if {@code needId} is null or already present.
     */
    public void addNeed(DevelopmentNeedId needId) {
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (status != DevelopmentPlanStatus.DRAFT && status != DevelopmentPlanStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot add needs to a plan with status=" + status);
        }
        if (needIds.contains(needId)) {
            throw new IllegalArgumentException("Need " + needId + " is already associated with this plan");
        }
        needIds.add(needId);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public TenantId getTenantId() { return tenantId; }

    public WorkerId getWorkerId() { return workerId; }

    public EmploymentId getEmploymentId() { return employmentId; }

    public int getCycleYear() { return cycleYear; }

    public DevelopmentPlanStatus getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    /** Returns an unmodifiable view of the associated need IDs. */
    public List<DevelopmentNeedId> getNeedIds() { return Collections.unmodifiableList(needIds); }
}
