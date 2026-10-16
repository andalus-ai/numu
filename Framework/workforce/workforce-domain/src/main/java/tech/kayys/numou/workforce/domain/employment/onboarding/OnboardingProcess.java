package tech.kayys.numou.workforce.domain.employment.onboarding;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.onboarding.event.OnboardingCompleted;
import tech.kayys.numou.workforce.domain.employment.onboarding.event.OnboardingInitiated;
import tech.kayys.numou.workforce.domain.employment.onboarding.event.OnboardingTaskCompleted;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class OnboardingProcess extends AbstractAggregateRoot<OnboardingProcessId> {

    private final TenantId tenantId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final LocalDate startDate;
    private LocalDate targetCompletionDate;
    private OnboardingProcessStatus status;
    private final List<OnboardingTask> tasks;

    private OnboardingProcess(
            OnboardingProcessId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate startDate,
            LocalDate targetCompletionDate
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.targetCompletionDate = targetCompletionDate;
        this.status = OnboardingProcessStatus.INITIATED;
        this.tasks = new ArrayList<>();
    }

    public static OnboardingProcess initiate(
            OnboardingProcessId id,
            TenantId tenantId,
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate startDate,
            LocalDate targetCompletionDate
    ) {
        OnboardingProcess process = new OnboardingProcess(id, tenantId, workerId, employmentId, startDate, targetCompletionDate);
        process.raise(new OnboardingInitiated(id, workerId, employmentId));
        return process;
    }

    public void addTask(OnboardingTask task) {
        if (status == OnboardingProcessStatus.COMPLETED || status == OnboardingProcessStatus.CANCELLED) {
            throw new IllegalStateException("Cannot add task to onboarding in status: " + status);
        }
        tasks.add(Objects.requireNonNull(task, "task must not be null"));
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void completeTask(String taskId, String completedBy, Instant now) {
        if (status == OnboardingProcessStatus.COMPLETED || status == OnboardingProcessStatus.CANCELLED) {
            throw new IllegalStateException("Cannot complete task in status: " + status);
        }
        OnboardingTask task = tasks.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.complete(completedBy, now);
        this.status = OnboardingProcessStatus.IN_PROGRESS;
        incrementVersion();
        this.updatedAt = now;
        raise(new OnboardingTaskCompleted(getId(), workerId, taskId, completedBy));

        boolean allRequiredCompleted = tasks.stream().filter(OnboardingTask::isRequired).allMatch(OnboardingTask::isCompleted);
        if (allRequiredCompleted) {
            complete();
        }
    }

    public void complete() {
        this.status = OnboardingProcessStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new OnboardingCompleted(getId(), workerId, employmentId));
    }

    public void cancel() {
        this.status = OnboardingProcessStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public WorkerId getWorkerId() { return workerId; }
    public EmploymentId getEmploymentId() { return employmentId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }
    public OnboardingProcessStatus getStatus() { return status; }
    public List<OnboardingTask> getTasks() { return Collections.unmodifiableList(tasks); }
}
