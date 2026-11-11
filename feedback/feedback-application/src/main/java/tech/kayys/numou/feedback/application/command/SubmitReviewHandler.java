package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.FeedbackRatingRepository;
import tech.kayys.numou.feedback.application.port.FeedbackReviewRepository;
import tech.kayys.numou.feedback.domain.review.FeedbackReview;
import tech.kayys.numou.feedback.domain.review.FeedbackReviewId;

import java.util.Objects;

/** Prevents cross-tenant or cross-target rating references from being attached to reviews. */
public final class SubmitReviewHandler {
    private final FeedbackRatingRepository ratings;
    private final FeedbackReviewRepository reviews;

    public SubmitReviewHandler(FeedbackRatingRepository ratings, FeedbackReviewRepository reviews) {
        this.ratings = Objects.requireNonNull(ratings, "ratings cannot be null");
        this.reviews = Objects.requireNonNull(reviews, "reviews cannot be null");
    }

    public FeedbackReviewId handle(SubmitReviewCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        var rating = ratings.findById(command.tenantId(), command.ratingId())
                .orElseThrow(() -> new IllegalArgumentException("Rating does not exist for this tenant"));
        if (rating.metric() != tech.kayys.numou.feedback.domain.rating.RatingMetric.STAR_RATING) {
            throw new IllegalArgumentException("A review must reference a STAR_RATING response");
        }
        if (!rating.target().equals(command.target())) {
            throw new IllegalArgumentException("Review target must match its rating target");
        }
        FeedbackReview review = FeedbackReview.submit(FeedbackReviewId.generate(),
                command.tenantId(), command.target(), command.author(), command.ratingId(),
                command.title(), command.body(), command.createdAt());
        reviews.save(review);
        return review.id();
    }
}