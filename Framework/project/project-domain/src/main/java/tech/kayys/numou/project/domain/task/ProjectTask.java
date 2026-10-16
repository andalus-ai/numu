package tech.kayys.syirkah.project.domain.task;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.event.TaskBlocked;
import tech.kayys.syirkah.project.domain.task.event.TaskCancelled;
import tech.kayys.syirkah.project.domain.task.event.TaskCompleted;
import tech.kayys.syirkah.project.domain.task.event.TaskCreated;
import tech.kayys.syirkah.project.domain.task.event.TaskStarted;
import tech.kayys.syirkah.project.domain.task.event.TaskUnblocked;

import java.util.Objects;

/**
 * A unit of work inside a project.
 *
 * A task knows its owning project, optional phase, optional
 * milestone, and optional parent task through identifiers only -
 * never object references. Loading the whole project graph just to
 * change one task is exactly what the aggregate boundary is meant
 * to prevent.
 *
 * Assignment, dependencies, comments, attachments, checklists, and
 * time entries each have an independent lifecycle and must live in
 * their own aggregates, not here.
 */
public final class ProjectTask
        extends AbstractAggregateRoot<ProjectTaskId> {

    private final ProjectId projectId;

    private final ProjectPhaseId phaseId;

    private final ProjectMilestoneId milestoneId;

    private final ProjectTaskId parentTaskId;

    private final TaskNumber taskNumber;

    private String title;

    private String description;

    private TaskType type;

    private TaskStatus status;

    private TaskPriority priority;

    private DateRange plannedPeriod;

    private int progressPercentage;

    private ProjectTask(
            ProjectTaskId id,
            ProjectId projectId,
            ProjectPhaseId phaseId,
            ProjectMilestoneId milestoneId,
            ProjectTaskId parentTaskId,
            TaskNumber taskNumber,
            String title,
            String description,
            TaskType type,
            TaskPriority priority,
            DateRange plannedPeriod
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        // Optional links; null means "not linked".
        this.phaseId = phaseId;
        this.milestoneId = milestoneId;
        this.parentTaskId = parentTaskId;

        this.taskNumber = Objects.requireNonNull(
                taskNumber,
                "taskNumber cannot be null"
        );

        this.title = requireText(title, "title");

        this.description = description;

        this.type = Objects.requireNonNull(
                type,
                "type cannot be null"
        );

        this.priority = Objects.requireNonNull(
                priority,
                "priority cannot be null"
        );

        this.plannedPeriod = plannedPeriod;
        this.status = TaskStatus.TODO;
        this.progressPercentage = 0;
    }

    public static ProjectTask create(
            ProjectTaskId id,
            ProjectId projectId,
            ProjectPhaseId phaseId,
            ProjectMilestoneId milestoneId,
            ProjectTaskId parentTaskId,
            TaskNumber taskNumber,
            String title,
            String description,
            TaskType type,
            TaskPriority priority,
            DateRange plannedPeriod
    ) {
        var task = new ProjectTask(
                id,
                projectId,
                phaseId,
                milestoneId,
                parentTaskId,
                taskNumber,
                title,
                description,
                type,
                priority,
                plannedPeriod
        );

        task.raise(
                TaskCreated.now(
                        id,
                        projectId,
                        task.title
                )
        );

        return task;
    }

    public void start() {

        if (status != TaskStatus.TODO) {
            throw new InvalidTaskStateException(
                    "Only TODO tasks can be started"
            );
        }

        status = TaskStatus.IN_PROGRESS;

        raise(
                TaskStarted.now(
                        id(),
                        projectId
                )
        );
    }

    public void block() {

        if (status != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException(
                    "Only in-progress tasks can be blocked"
            );
        }

        status = TaskStatus.BLOCKED;

        raise(
                TaskBlocked.now(
                        id(),
                        projectId
                )
        );
    }

    public void unblock() {

        if (status != TaskStatus.BLOCKED) {
            throw new InvalidTaskStateException(
                    "Only blocked tasks can be unblocked"
            );
        }

        status = TaskStatus.IN_PROGRESS;

        raise(
                TaskUnblocked.now(
                        id(),
                        projectId
                )
        );
    }

    public void complete() {

        if (status != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException(
                    "Only in-progress tasks can be completed"
            );
        }

        progressPercentage = 100;
        status = TaskStatus.DONE;

        raise(
                TaskCompleted.now(
                        id(),
                        projectId
                )
        );
    }

    public void cancel() {

        if (status == TaskStatus.DONE) {
            throw new InvalidTaskStateException(
                    "Completed task cannot be cancelled"
            );
        }

        if (status == TaskStatus.CANCELLED) {
            throw new InvalidTaskStateException(
                    "Task is already cancelled"
            );
        }

        status = TaskStatus.CANCELLED;

        raise(
                TaskCancelled.now(
                        id(),
                        projectId
                )
        );
    }

    public void updateProgress(int percentage) {

        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "Progress must be between 0 and 100"
            );
        }

        if (status == TaskStatus.CANCELLED) {
            throw new InvalidTaskStateException(
                    "Cancelled task cannot be updated"
            );
        }

        progressPercentage = percentage;

        if (percentage == 100
                && status == TaskStatus.IN_PROGRESS) {

            complete();
        }
    }

    public void rename(String title) {
        this.title = requireText(title, "title");
    }

    public void changePriority(TaskPriority priority) {
        this.priority = Objects.requireNonNull(
                priority,
                "priority cannot be null"
        );
    }

    public void changePlannedPeriod(DateRange plannedPeriod) {
        this.plannedPeriod = plannedPeriod;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectPhaseId phaseId() {
        return phaseId;
    }

    public ProjectMilestoneId milestoneId() {
        return milestoneId;
    }

    public ProjectTaskId parentTaskId() {
        return parentTaskId;
    }

    public TaskNumber taskNumber() {
        return taskNumber;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public TaskType type() {
        return type;
    }

    public TaskStatus status() {
        return status;
    }

    public TaskPriority priority() {
        return priority;
    }

    public DateRange plannedPeriod() {
        return plannedPeriod;
    }

    public int progressPercentage() {
        return progressPercentage;
    }

    private static String requireText(
            String value,
            String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " cannot be blank"
            );
        }

        return value.trim();
    }
}