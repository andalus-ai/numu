package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;

import java.util.Objects;

public record SubmitPerformanceReviewCommand(
        PerformanceReviewId reviewId,
        String summary
) implements Command {
    public SubmitPerformanceReviewCommand {
        Objects.requireNonNull(reviewId, "reviewId must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
