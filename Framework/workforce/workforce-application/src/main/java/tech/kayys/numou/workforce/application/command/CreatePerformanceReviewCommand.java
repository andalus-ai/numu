package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

public record CreatePerformanceReviewCommand(
        PerformanceCycleId cycleId,
        WorkerId workerId,
        EmploymentId employmentId,
        WorkerId reviewerWorkerId
) implements Command {
    public CreatePerformanceReviewCommand {
        Objects.requireNonNull(cycleId, "cycleId must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(employmentId, "employmentId must not be null");
        Objects.requireNonNull(reviewerWorkerId, "reviewerWorkerId must not be null");
    }
}
