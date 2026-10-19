package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.workforce.domain.development.event.DevelopmentActivityCompleted;
import tech.kayys.numou.workforce.domain.development.event.DevelopmentActivityPlanned;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;

/**
 * Aggregate root representing a concrete development activity undertaken by a worker
 * to address a {@link DevelopmentNeed}.
 *
 * <p>Activities are the actionable items (trainings, mentoring sessions, etc.) that
 * move a need from identified to addressed. This aggregate tracks planned and actual
 * dates, the external provider, and an optional external reference (e.g. LMS course ID).</p>
 *
 * <p>Lifecycle: {@code PLANNED → IN_PROGRESS → COMPLETED}; any non-terminal state can
 * transition to {@code CANCELLED}.</p>
 */
public class DevelopmentActivity extends AbstractAggregateRoot<DevelopmentActivityId> {

    private final DevelopmentNeedId needId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final DevelopmentActivityType type;
    private final String title;
    private String description;

    private final LocalDate plannedStart;
    private final LocalDate plannedEnd;
    private LocalDate actualStart;
    private LocalDate actualEnd;

    private DevelopmentActivityStatus status;

    private final String providerName;
    private final String externalReference;

    // -------------------------------------------------------------------------
    // Private constructor – use factory method
    // -------------------------------------------------------------------------

    private DevelopmentActivity(
            DevelopmentActivityId id,
            DevelopmentNeedId needId,
            WorkerId workerId,
            EmploymentId employmentId,
            DevelopmentActivityType type,
            String title,
            String description,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            LocalDate actualStart,
            LocalDate actualEnd,
            DevelopmentActivityStatus status,
            String providerName,
            String externalReference) {
        super(id);
        this.needId = needId;
        this.workerId = workerId;
        this.employmentId = employmentId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.actualStart = actualStart;
        this.actualEnd = actualEnd;
        this.status = status;
        this.providerName = providerName;
        this.externalReference = externalReference;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code DevelopmentActivity} in {@code PLANNED} status and
     * registers the {@link DevelopmentActivityPlanned} domain event.
     *
     * @param id                the unique identity for this activity.
     * @param needId            the parent development need.
     * @param workerId          the worker this activity is for.
     * @param employmentId      the active employment record.
     * @param type              the category of this activity.
     * @param title             a short, human-readable title.
     * @param description       optional detailed description.
     * @param plannedStart      the date the activity is expected to start.
     * @param plannedEnd        the date the activity is expected to end.
     * @param providerName      optional name of the external training provider.
     * @param externalReference optional external reference (e.g. LMS course ID).
     */
    public static DevelopmentActivity create(
            DevelopmentActivityId id,
            DevelopmentNeedId needId,
            WorkerId workerId,
            EmploymentId employmentId,
            DevelopmentActivityType type,
            String title,
            String description,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            String providerName,
            String externalReference) {

        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (needId == null) throw new IllegalArgumentException("needId must not be null");
        if (workerId == null) throw new IllegalArgumentException("workerId must not be null");
        if (employmentId == null) throw new IllegalArgumentException("employmentId must not be null");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (plannedStart == null) throw new IllegalArgumentException("plannedStart must not be null");
        if (plannedEnd != null && plannedEnd.isBefore(plannedStart))
            throw new IllegalArgumentException("plannedEnd must not be before plannedStart");

        DevelopmentActivity activity = new DevelopmentActivity(
                id, needId, workerId, employmentId, type, title, description,
                plannedStart, plannedEnd, null, null,
                DevelopmentActivityStatus.PLANNED, providerName, externalReference);

        activity.registerEvent(new DevelopmentActivityPlanned(id, workerId, type));
        return activity;
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Starts the activity, transitioning from {@code PLANNED} to {@code IN_PROGRESS}.
     *
     * @param actualStart the date the activity actually began.
     * @throws IllegalStateException if the activity is not in {@code PLANNED} status.
     */
    public void start(LocalDate actualStart) {
        if (status != DevelopmentActivityStatus.PLANNED) {
            throw new IllegalStateException(
                    "DevelopmentActivity can only be started from PLANNED status; current=" + status);
        }
        if (actualStart == null) throw new IllegalArgumentException("actualStart must not be null");
        this.actualStart = actualStart;
        this.status = DevelopmentActivityStatus.IN_PROGRESS;
    }

    /**
     * Completes the activity, transitioning from {@code IN_PROGRESS} to {@code COMPLETED},
     * and registers the {@link DevelopmentActivityCompleted} event.
     *
     * @param actualEnd the date the activity actually finished.
     * @throws IllegalStateException if the activity is not in {@code IN_PROGRESS} status.
     */
    public void complete(LocalDate actualEnd) {
        if (status != DevelopmentActivityStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "DevelopmentActivity can only be completed from IN_PROGRESS status; current=" + status);
        }
        if (actualEnd == null) throw new IllegalArgumentException("actualEnd must not be null");
        if (this.actualStart != null && actualEnd.isBefore(this.actualStart))
            throw new IllegalArgumentException("actualEnd must not be before actualStart");
        this.actualEnd = actualEnd;
        this.status = DevelopmentActivityStatus.COMPLETED;
        registerEvent(new DevelopmentActivityCompleted(getId(), workerId));
    }

    /**
     * Cancels the activity from any non-terminal state.
     *
     * @param reason a mandatory reason for cancellation (for audit purposes).
     * @throws IllegalStateException if the activity is already {@code COMPLETED} or {@code CANCELLED}.
     */
    public void cancel(String reason) {
        if (status == DevelopmentActivityStatus.COMPLETED || status == DevelopmentActivityStatus.CANCELLED) {
            throw new IllegalStateException(
                    "DevelopmentActivity cannot be cancelled from terminal status=" + status);
        }
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("A cancellation reason must be provided");
        this.status = DevelopmentActivityStatus.CANCELLED;
        // Reason is accepted for audit; further storage can be handled via a domain event if needed.
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public DevelopmentNeedId getNeedId() { return needId; }

    public WorkerId getWorkerId() { return workerId; }

    public EmploymentId getEmploymentId() { return employmentId; }

    public DevelopmentActivityType getType() { return type; }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public LocalDate getPlannedStart() { return plannedStart; }

    public LocalDate getPlannedEnd() { return plannedEnd; }

    public LocalDate getActualStart() { return actualStart; }

    public LocalDate getActualEnd() { return actualEnd; }

    public DevelopmentActivityStatus getStatus() { return status; }

    public String getProviderName() { return providerName; }

    public String getExternalReference() { return externalReference; }
}
