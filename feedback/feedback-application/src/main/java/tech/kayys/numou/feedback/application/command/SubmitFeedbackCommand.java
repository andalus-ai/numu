package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackContext;
import tech.kayys.numou.feedback.domain.FeedbackKind;
import tech.kayys.numou.feedback.domain.FeedbackPriority;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

public record SubmitFeedbackCommand(
        TenantId tenantId,
        FeedbackActor author,
        FeedbackKind kind,
        FeedbackPriority priority,
        String title,
        String description,
        FeedbackContext context,
        String idempotencyKey,
        Instant occurredAt) {}