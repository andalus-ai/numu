package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.FeedbackPriority;
import tech.kayys.numou.feedback.domain.FeedbackType;

import java.time.Instant;
import java.util.UUID;

public record SubmitFeedbackCommand(
        UUID tenantId,
        UUID submitterId,
        FeedbackType type,
        String title,
        String description,
        String sourceApplication,
        String sourceModule,
        String sourceReference,
        FeedbackPriority priority,
        String idempotencyKey,
        Instant occurredAt) {}