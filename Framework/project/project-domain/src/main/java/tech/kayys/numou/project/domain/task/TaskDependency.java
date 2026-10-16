package tech.kayys.syirkah.project.domain.task;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/**
 * A scheduling relationship between two tasks.
 *
 * Dependencies are stored outside {@code ProjectTask} so the task
 * aggregate stays small; the Gantt/scheduling capability consumes
 * these predecessors when it computes dates.
 */
public final class TaskDependency
        extends AbstractAggregateRoot<TaskDependencyId> {

    private final ProjectId projectId;

    private final ProjectTaskId predecessorTaskId;

    private final ProjectTaskId successorTaskId;

    private final TaskDependencyType dependencyType;

    private final int lagDays;

    private TaskDependency(
            TaskDependencyId id,
            ProjectId projectId,
            ProjectTaskId predecessorTaskId,
            ProjectTaskId successorTaskId,
            TaskDependencyType dependencyType,
            int lagDays
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.predecessorTaskId = Objects.requireNonNull(
                predecessorTaskId,
                "predecessorTaskId cannot be null"
        );

        this.successorTaskId = Objects.requireNonNull(
                successorTaskId,
                "successorTaskId cannot be null"
        );

        if (predecessorTaskId.equals(successorTaskId)) {
            throw new IllegalArgumentException(
                    "A task cannot depend on itself"
            );
        }

        this.dependencyType = Objects.requireNonNull(
                dependencyType,
                "dependencyType cannot be null"
        );

        if (lagDays < 0) {
            throw new IllegalArgumentException(
                    "Lag days cannot be negative"
            );
        }

        this.lagDays = lagDays;
    }

    public static TaskDependency link(
            TaskDependencyId id,
            ProjectId projectId,
            ProjectTaskId predecessorTaskId,
            ProjectTaskId successorTaskId,
            TaskDependencyType dependencyType,
            int lagDays
    ) {
        return new TaskDependency(
                id,
                projectId,
                predecessorTaskId,
                successorTaskId,
                dependencyType,
                lagDays
        );
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectTaskId predecessorTaskId() {
        return predecessorTaskId;
    }

    public ProjectTaskId successorTaskId() {
        return successorTaskId;
    }

    public TaskDependencyType dependencyType() {
        return dependencyType;
    }

    public int lagDays() {
        return lagDays;
    }
}
