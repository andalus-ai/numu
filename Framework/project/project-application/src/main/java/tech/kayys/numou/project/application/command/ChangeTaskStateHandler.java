package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.task.ProjectTask;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies one state transition to an existing task and publishes
 * the task events.
 *
 * Illegal transitions are rejected by the aggregate itself
 * (InvalidTaskStateException); the handler only deals with the
 * missing-task case and persistence.
 */
public final class ChangeTaskStateHandler {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("TASK_NOT_FOUND", "Project task does not exist");

    private final ProjectTaskRepository tasks;
    private final EventPublisher eventPublisher;

    public ChangeTaskStateHandler(
            ProjectTaskRepository tasks,
            EventPublisher eventPublisher
    ) {
        this.tasks = Objects.requireNonNull(tasks);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public Uni<Result<ProjectTaskId>> start(StartTaskCommand command) {
        return transition(command.taskId(), ProjectTask::start);
    }

    public Uni<Result<ProjectTaskId>> block(BlockTaskCommand command) {
        return transition(command.taskId(), ProjectTask::block);
    }

    public Uni<Result<ProjectTaskId>> unblock(UnblockTaskCommand command) {
        return transition(command.taskId(), ProjectTask::unblock);
    }

    public Uni<Result<ProjectTaskId>> complete(CompleteTaskCommand command) {
        return transition(command.taskId(), ProjectTask::complete);
    }

    public Uni<Result<ProjectTaskId>> cancel(CancelTaskCommand command) {
        return transition(command.taskId(), ProjectTask::cancel);
    }

    private Uni<Result<ProjectTaskId>> transition(
            ProjectTaskId taskId,
            Consumer<ProjectTask> transition
    ) {
        return Uni.createFrom()
                .completionStage(tasks.findById(taskId))
                .onItem()
                .transformToUni(maybeTask -> {

                    if (maybeTask.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var task = maybeTask.get();
                    transition.accept(task);

                    return Uni.createFrom()
                            .completionStage(tasks.save(task))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}