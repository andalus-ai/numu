package tech.kayys.numou.feedback.domain;

import java.time.Instant;
import java.util.UUID;

public record FeedbackSubmitted(UUID eventId, FeedbackId feedbackId, UUID tenantId,
                                FeedbackType type, Instant occurredAt) implements FeedbackEvent {}