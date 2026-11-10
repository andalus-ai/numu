package tech.kayys.numou.feedback.domain;

import java.time.Instant;
import java.util.UUID;

public record FeedbackStatusChanged(UUID eventId, FeedbackId feedbackId, UUID tenantId,
                                    FeedbackStatus previousStatus, FeedbackStatus newStatus,
                                    Instant occurredAt) implements FeedbackEvent {}