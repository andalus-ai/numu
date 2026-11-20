package tech.kayys.numou.feedback.domain.event;

import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FeedbackSubmitted(UUID eventId, Instant occurredAt,
                                FeedbackId feedbackId, String kindCode) implements DomainEvent {
    public FeedbackSubmitted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(feedbackId, "feedbackId cannot be null");
        Objects.requireNonNull(kindCode, "kindCode cannot be null");
    }
    @Override public String eventType() { return "feedback.submitted.v1"; }
}