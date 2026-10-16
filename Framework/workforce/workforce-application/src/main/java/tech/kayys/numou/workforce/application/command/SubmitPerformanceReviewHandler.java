package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.performance.PerformanceReview;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;
import tech.kayys.numou.workforce.spi.port.PerformanceReviewRepository;

import java.util.Objects;

public class SubmitPerformanceReviewHandler implements CommandHandler<SubmitPerformanceReviewCommand, Result<PerformanceReviewId>> {

    private final PerformanceReviewRepository repository;
    private final EventPublisher eventPublisher;

    public SubmitPerformanceReviewHandler(PerformanceReviewRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PerformanceReviewId>> handle(SubmitPerformanceReviewCommand cmd) {
        return Uni.createFrom().completionStage(repository.findById(cmd.reviewId()))
                .chain(optReview -> {
                    if (optReview.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("REVIEW_NOT_FOUND", "Performance review not found")));
                    }
                    PerformanceReview review = optReview.get();
                    try {
                        review.submit(cmd.summary());
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("SUBMIT_FAILED", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(review))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
