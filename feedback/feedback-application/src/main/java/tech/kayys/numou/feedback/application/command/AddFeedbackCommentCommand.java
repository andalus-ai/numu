package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

public record AddFeedbackCommentCommand(
        TenantId tenantId,
        FeedbackId feedbackId,
        FeedbackCommentId parentCommentId,
        FeedbackActor author,
        FeedbackCommentVisibility visibility,
        String body,
        String idempotencyKey,
        Instant createdAt) {
}