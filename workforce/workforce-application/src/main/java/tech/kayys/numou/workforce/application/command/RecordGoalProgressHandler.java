package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoal;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;
import tech.kayys.numou.workforce.spi.port.PerformanceGoalRepository;

import java.util.Objects;

public class RecordGoalProgressHandler implements CommandHandler<RecordGoalProgressCommand, Result<PerformanceGoalId>> {

    private final PerformanceGoalRepository repository;
    private final EventPublisher eventPublisher;

    public RecordGoalProgressHandler(PerformanceGoalRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PerformanceGoalId>> handle(RecordGoalProgressCommand cmd) {
        return Uni.createFrom().completionStage(repository.findById(cmd.goalId()))
                .chain(optGoal -> {
                    if (optGoal.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("GOAL_NOT_FOUND", "Goal not found")));
                    }
                    PerformanceGoal goal = optGoal.get();
                    try {
                        goal.recordProgress(cmd.progress());
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PROGRESS_FAILED", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(goal))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
