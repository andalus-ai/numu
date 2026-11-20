package tech.kayys.numou.feedback.domain.event;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.FeedbackStatus;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FeedbackStatusChanged(UUID eventId, Instant occurredAt, FeedbackId feedbackId,
                                    FeedbackStatus previousStatus, FeedbackStatus newStatus)
        implements DomainEvent {
    public FeedbackStatusChanged {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        Objects.requireNonNull(previousStatus, "previousStatus cannot be null");
        Objects.requireNonNull(newStatus, "newStatus cannot be null");
    }
    @Override public String eventType() { return "feedback.status-changed.v1"; }
}