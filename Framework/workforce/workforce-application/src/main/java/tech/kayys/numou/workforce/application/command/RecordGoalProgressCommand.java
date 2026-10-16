package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;

import java.math.BigDecimal;
import java.util.Objects;

public record RecordGoalProgressCommand(
        PerformanceGoalId goalId,
        BigDecimal progress
) implements Command {
    public RecordGoalProgressCommand {
        Objects.requireNonNull(goalId, "goalId must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
    }
}
