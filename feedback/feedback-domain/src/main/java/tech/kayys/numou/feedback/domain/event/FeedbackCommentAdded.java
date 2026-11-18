package tech.kayys.numou.feedback.domain.event;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FeedbackCommentAdded(
        UUID eventId,
        Instant occurredAt,
        FeedbackId feedbackId,
        FeedbackCommentId commentId,
        FeedbackCommentId parentCommentId,
        FeedbackCommentVisibility visibility) implements DomainEvent {

    public FeedbackCommentAdded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        Objects.requireNonNull(commentId, "commentId cannot be null");
        Objects.requireNonNull(visibility, "visibility cannot be null");
    }

    @Override public String eventType() { return "feedback.comment-added.v1"; }
}