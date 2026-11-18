package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.FeedbackId;
import tech.kayys.numou.feedback.domain.comment.FeedbackCommentVisibility;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * Host-provided authorization boundary. Implementations delegate to the
 * Security capability and must not infer permission from actor identifiers.
 */
public interface FeedbackCommentAccessPolicy {
    void assertCanAdd(TenantId tenantId, FeedbackId feedbackId,
                      FeedbackActor actor, FeedbackCommentVisibility visibility);

    void assertCanRead(TenantId tenantId, FeedbackId feedbackId,
                       FeedbackActor actor, FeedbackCommentVisibility visibility);
}