package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.task.TaskAssignment;
import tech.kayys.syirkah.project.domain.task.TaskAssignmentId;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;
import tech.kayys.syirkah.project.spi.port.TaskAssignmentRepository;

import java.util.Objects;

/**
 * Assigns a resource to a task that exists, and releases an
 * assignment when asked.
 */
public final class AssignTaskHandler
        implements CommandHandler<AssignTaskCommand, Result<TaskAssignmentId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("TASK_NOT_FOUND", "Project task does not exist");

    private final ProjectTaskRepository tasks;
    private final TaskAssignmentRepository assignments;

    public AssignTaskHandler(
            ProjectTaskRepository tasks,
            TaskAssignmentRepository assignments
    ) {
        this.tasks = Objects.requireNonNull(tasks);
        this.assignments = Objects.requireNonNull(assignments);
    }

    @Override
    public Uni<Result<TaskAssignmentId>> handle(AssignTaskCommand command) {
        return Uni.createFrom()
                .completionStage(tasks.findById(command.taskId()))
                .onItem()
                .transformToUni(maybeTask -> {

                    if (maybeTask.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    if (!maybeTask.get().projectId().equals(command.projectId())) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var assignment = TaskAssignment.assign(
                            TaskAssignmentId.newId(),
                            command.projectId(),
                            command.taskId(),
                            command.resourceType(),
                            command.resourceId(),
                            command.role(),
                            command.plannedHours()
                    );

                    return Uni.createFrom()
                            .completionStage(assignments.save(assignment))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
