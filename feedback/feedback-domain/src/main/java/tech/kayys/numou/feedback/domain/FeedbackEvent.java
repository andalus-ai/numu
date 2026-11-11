package tech.kayys.numou.feedback.domain;

import java.time.Instant;
import java.util.UUID;

/** Domain event envelope. Persistence and durable publication are application/infrastructure concerns. */
public sealed interface FeedbackEvent permits FeedbackSubmitted, FeedbackStatusChanged {
    UUID eventId();
    FeedbackId feedbackId();
    UUID tenantId();
    Instant occurredAt();
}