package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.task.TaskAssignmentId;
import tech.kayys.syirkah.project.spi.port.TaskAssignmentRepository;

import java.util.Objects;

/**
 * Releases an assignment: the task stays, the resource leaves.
 */
public final class UnassignTaskHandler
        implements CommandHandler<UnassignTaskCommand, Result<TaskAssignmentId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("ASSIGNMENT_NOT_FOUND", "Task assignment does not exist");

    private final TaskAssignmentRepository assignments;

    public UnassignTaskHandler(TaskAssignmentRepository assignments) {
        this.assignments = Objects.requireNonNull(assignments);
    }

    @Override
    public Uni<Result<TaskAssignmentId>> handle(UnassignTaskCommand command) {
        return Uni.createFrom()
                .completionStage(assignments.findById(command.assignmentId()))
                .onItem()
                .transformToUni(maybeAssignment -> {

                    if (maybeAssignment.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var assignment = maybeAssignment.get();

                    if (!assignment.projectId().equals(command.projectId())
                            || !assignment.taskId().equals(command.taskId())) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    assignment.unassign();

                    return Uni.createFrom()
                            .completionStage(assignments.save(assignment))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
