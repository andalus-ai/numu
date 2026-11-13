package tech.kayys.numou.feedback.domain.review;

import java.util.Objects;
import java.util.UUID;

public record FeedbackReviewId(UUID value) {
    public FeedbackReviewId { Objects.requireNonNull(value, "value cannot be null"); }
    public static FeedbackReviewId generate() { return new FeedbackReviewId(UUID.randomUUID()); }
    public static FeedbackReviewId of(UUID value) { return new FeedbackReviewId(value); }
}