package tech.kayys.numou.feedback.domain.rating;

import java.util.Objects;
import java.util.UUID;

public record FeedbackRatingId(UUID value) {
    public FeedbackRatingId { Objects.requireNonNull(value, "value cannot be null"); }
    public static FeedbackRatingId generate() { return new FeedbackRatingId(UUID.randomUUID()); }
    public static FeedbackRatingId of(UUID value) { return new FeedbackRatingId(value); }
}