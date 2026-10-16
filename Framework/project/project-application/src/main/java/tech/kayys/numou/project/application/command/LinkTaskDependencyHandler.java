package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.task.TaskDependency;
import tech.kayys.syirkah.project.domain.task.TaskDependencyId;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;
import tech.kayys.syirkah.project.spi.port.TaskDependencyRepository;

import java.util.Objects;

/**
 * Links a successor task behind a predecessor task.
 *
 * Both ends must exist and belong to the same project; a task can
 * never depend on itself.
 */
public final class LinkTaskDependencyHandler
        implements CommandHandler<LinkTaskDependencyCommand, Result<TaskDependencyId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("TASK_NOT_FOUND", "Project task does not exist");

    private final ProjectTaskRepository tasks;
    private final TaskDependencyRepository dependencies;

    public LinkTaskDependencyHandler(
            ProjectTaskRepository tasks,
            TaskDependencyRepository dependencies
    ) {
        this.tasks = Objects.requireNonNull(tasks);
        this.dependencies = Objects.requireNonNull(dependencies);
    }

    @Override
    public Uni<Result<TaskDependencyId>> handle(LinkTaskDependencyCommand command) {
        return Uni.createFrom()
                .completionStage(tasks.findById(command.predecessorTaskId()))
                .onItem()
                .transformToUni(maybePredecessor -> Uni.createFrom()
                        .completionStage(tasks.findById(command.successorTaskId()))
                        .onItem()
                        .transformToUni(maybeSuccessor -> {

                            if (maybePredecessor.isEmpty() || maybeSuccessor.isEmpty()) {
                                return Uni.createFrom().item(Result.failure(NOT_FOUND));
                            }

                            var predecessor = maybePredecessor.get();
                            var successor = maybeSuccessor.get();

                            if (!predecessor.projectId().equals(command.projectId())
                                    || !successor.projectId().equals(command.projectId())) {
                                return Uni.createFrom().item(Result.failure(NOT_FOUND));
                            }

                            var dependency = TaskDependency.link(
                                    TaskDependencyId.newId(),
                                    command.projectId(),
                                    command.predecessorTaskId(),
                                    command.successorTaskId(),
                                    command.dependencyType(),
                                    command.lagDays()
                            );

                            return Uni.createFrom()
                                    .completionStage(dependencies.save(dependency))
                                    .map(saved -> Result.success(saved.id()));
                        }));
    }
}
