package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceGoalAchieved;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceGoalActivated;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceGoalCreated;
import tech.kayys.numou.workforce.domain.performance.event.PerformanceGoalProgressRecorded;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class PerformanceGoal extends AbstractAggregateRoot<PerformanceGoalId> {

    private final TenantId tenantId;
    private final PerformanceCycleId cycleId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private String title;
    private String description;
    private final GoalType type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private GoalStatus status;
    private BigDecimal targetValue;
    private String targetUnit;
    private BigDecimal achievedValue;

    private PerformanceGoal(
            PerformanceGoalId id,
            TenantId tenantId,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            String title,
            String description,
            GoalType type,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal targetValue,
            String targetUnit
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.cycleId = Objects.requireNonNull(cycleId, "cycleId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.description = description;
        this.targetValue = targetValue;
        this.targetUnit = targetUnit;
        this.achievedValue = BigDecimal.ZERO;
        this.status = GoalStatus.DRAFT;
    }

    public static PerformanceGoal create(
            PerformanceGoalId id,
            TenantId tenantId,
            PerformanceCycleId cycleId,
            WorkerId workerId,
            EmploymentId employmentId,
            String title,
            String description,
            GoalType type,
            LocalDate startDate,
            LocalDate dueDate,
            BigDecimal targetValue,
            String targetUnit
    ) {
        PerformanceGoal goal = new PerformanceGoal(id, tenantId, cycleId, workerId, employmentId, title, description, type, startDate, dueDate, targetValue, targetUnit);
        goal.raise(new PerformanceGoalCreated(id, workerId, cycleId));
        return goal;
    }

    public void activate() {
        if (status != GoalStatus.DRAFT) {
            throw new IllegalStateException("Only draft goals can be activated");
        }
        this.status = GoalStatus.ACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceGoalActivated(getId()));
    }

    public void recordProgress(BigDecimal progress) {
        if (status != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Can only record progress on active goals");
        }
        this.achievedValue = progress;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceGoalProgressRecorded(getId(), progress));
    }

    public void achieve() {
        if (status != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Only active goals can be achieved");
        }
        this.status = GoalStatus.ACHIEVED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new PerformanceGoalAchieved(getId()));
    }

    public void partiallyAchieve() {
        if (status != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Only active goals can be marked partially achieved");
        }
        this.status = GoalStatus.PARTIALLY_ACHIEVED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void markNotAchieved() {
        if (status != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Only active goals can be marked not achieved");
        }
        this.status = GoalStatus.NOT_ACHIEVED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == GoalStatus.ACHIEVED) {
            throw new IllegalStateException("Achieved goals cannot be cancelled");
        }
        this.status = GoalStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public PerformanceCycleId getCycleId() { return cycleId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public GoalType getType() { return type; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public GoalStatus getStatus() { return status; }
    public BigDecimal getTargetValue() { return targetValue; }
    public String getTargetUnit() { return targetUnit; }
    public BigDecimal getAchievedValue() { return achievedValue; }
}
