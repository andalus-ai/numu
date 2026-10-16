package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.development.event.DevelopmentNeedAdded;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;

/**
 * Aggregate root representing a single identified development need for a worker.
 *
 * <p>A {@code DevelopmentNeed} is always associated with a parent
 * {@link DevelopmentPlan} via {@code planId}. It captures what gap or improvement
 * target has been identified and tracks whether it has been addressed through
 * one or more {@link DevelopmentActivity} instances.</p>
 *
 * <p>Lifecycle: {@code OPEN → IN_PROGRESS → ADDRESSED}; any non-terminal state
 * can transition to {@code CANCELLED}.</p>
 */
public class DevelopmentNeed extends AbstractAggregateRoot<DevelopmentNeedId> {

    private final DevelopmentPlanId planId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final DevelopmentNeedType type;
    private DevelopmentNeedPriority priority;
    private final String title;
    private String description;
    private LocalDate targetDate;
    private DevelopmentNeedStatus status;

    // -------------------------------------------------------------------------
    // Private constructor – use factory method
    // -------------------------------------------------------------------------

    private DevelopmentNeed(
            DevelopmentNeedId id,
            DevelopmentPlanId planId,
            WorkerId workerId,
            EmploymentId employmentId,
            DevelopmentNeedType type,
            DevelopmentNeedPriority priority,
            String title,
            String description,
            LocalDate targetDate,
            DevelopmentNeedStatus status) {
        super(id);
        this.planId = planId;
        this.workerId = workerId;
        this.employmentId = employmentId;
        this.type = type;
        this.priority = priority;
        this.title = title;
        this.description = description;
        this.targetDate = targetDate;
        this.status = status;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code DevelopmentNeed} in {@code OPEN} status and registers
     * the {@link DevelopmentNeedAdded} domain event.
     */
    public static DevelopmentNeed create(
            DevelopmentNeedId id,
            DevelopmentPlanId planId,
            WorkerId workerId,
            EmploymentId employmentId,
            DevelopmentNeedType type,
            DevelopmentNeedPriority priority,
            String title,
            String description,
            LocalDate targetDate) {

        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (planId == null) throw new IllegalArgumentException("planId must not be null");
        if (workerId == null) throw new IllegalArgumentException("workerId must not be null");
        if (employmentId == null) throw new IllegalArgumentException("employmentId must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (priority == null) throw new IllegalArgumentException("priority must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");

        DevelopmentNeed need = new DevelopmentNeed(
                id, planId, workerId, employmentId, type, priority,
                title, description, targetDate, DevelopmentNeedStatus.OPEN);

        need.registerEvent(new DevelopmentNeedAdded(id, planId, workerId));
        return need;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Marks this need as currently in progress.
     * Transitions {@code OPEN → IN_PROGRESS}.
     *
     * @throws IllegalStateException if the need is not in {@code OPEN} status.
     */
    public void startProgress() {
        if (status != DevelopmentNeedStatus.OPEN) {
            throw new IllegalStateException(
                    "DevelopmentNeed can only be started from OPEN status; current=" + status);
        }
        this.status = DevelopmentNeedStatus.IN_PROGRESS;
    }

    /**
     * Marks this need as fully addressed.
     * Transitions {@code OPEN|IN_PROGRESS → ADDRESSED}.
     *
     * @throws IllegalStateException if the need is already in a terminal state.
     */
    public void address() {
        if (status == DevelopmentNeedStatus.ADDRESSED || status == DevelopmentNeedStatus.CANCELLED) {
            throw new IllegalStateException(
                    "DevelopmentNeed cannot be addressed from terminal status=" + status);
        }
        this.status = DevelopmentNeedStatus.ADDRESSED;
    }

    /**
     * Cancels this need from any non-terminal state.
     *
     * @throws IllegalStateException if the need is already {@code ADDRESSED} or {@code CANCELLED}.
     */
    public void cancel() {
        if (status == DevelopmentNeedStatus.ADDRESSED || status == DevelopmentNeedStatus.CANCELLED) {
            throw new IllegalStateException(
                    "DevelopmentNeed cannot be cancelled from terminal status=" + status);
        }
        this.status = DevelopmentNeedStatus.CANCELLED;
    }

    /** Updates the priority of this need. */
    public void updatePriority(DevelopmentNeedPriority newPriority) {
        if (newPriority == null) throw new IllegalArgumentException("priority must not be null");
        this.priority = newPriority;
    }

    /** Updates the description. */
    public void updateDescription(String description) {
        this.description = description;
    }

    /** Updates the target date. */
    public void updateTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public DevelopmentPlanId getPlanId() { return planId; }

    public WorkerId getWorkerId() { return workerId; }

    public EmploymentId getEmploymentId() { return employmentId; }

    public DevelopmentNeedType getType() { return type; }

    public DevelopmentNeedPriority getPriority() { return priority; }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public LocalDate getTargetDate() { return targetDate; }

    public DevelopmentNeedStatus getStatus() { return status; }
}
