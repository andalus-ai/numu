package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.domain.rating.FeedbackRating;
import tech.kayys.numou.feedback.domain.rating.FeedbackRatingId;

import java.util.Objects;

public final class SubmitRatingHandler {
    private final FeedbackRatingRepository ratings;

    public SubmitRatingHandler(FeedbackRatingRepository ratings) {
        this.ratings = Objects.requireNonNull(ratings, "ratings cannot be null");
    }

    public FeedbackRatingId handle(SubmitRatingCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        FeedbackRating rating = FeedbackRating.submit(FeedbackRatingId.generate(),
                command.tenantId(), command.target(), command.metric(), command.scale(),
                command.value(), command.respondent(), command.surveyReference(), command.submittedAt());
        return ratings.save(rating, command.idempotencyKey().trim());
    }
}