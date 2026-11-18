package tech.kayys.numou.feedback.domain.comment;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FeedbackCommentId(UUID value) implements DomainId<UUID> {
    public FeedbackCommentId { Objects.requireNonNull(value, "value cannot be null"); }
    public static FeedbackCommentId generate() { return new FeedbackCommentId(UUID.randomUUID()); }
    public static FeedbackCommentId of(UUID value) { return new FeedbackCommentId(value); }
    @Override public String toString() { return value.toString(); }
}