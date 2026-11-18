package tech.kayys.numou.feedback.application.query;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record ListFeedbackCommentsQuery(
        TenantId tenantId,
        FeedbackId feedbackId,
        FeedbackActor requester,
        FeedbackCommentVisibility visibility,
        int limit,
        int offset) {
}