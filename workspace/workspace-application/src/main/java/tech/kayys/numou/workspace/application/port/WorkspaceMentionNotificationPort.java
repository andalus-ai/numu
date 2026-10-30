package tech.kayys.numou.workspace.application.port;

import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral bridge to the Notification capability.
 * Implementations must persist recipient-scoped idempotently before returning.
 */
public interface WorkspaceMentionNotificationPort {
    void notifyMention(MentionNotification notification);

    record MentionNotification(
            UUID tenantId,
            UUID recipientId,
            UUID actorId,
            UUID pageId,
            UUID commentId,
            String recipientHandle,
            String actorDisplayName,
            String pageTitle,
            String idempotencyKey) {
        public MentionNotification {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(recipientId, "recipientId is required");
            Objects.requireNonNull(actorId, "actorId is required");
            Objects.requireNonNull(pageId, "pageId is required");
            Objects.requireNonNull(commentId, "commentId is required");
            Objects.requireNonNull(recipientHandle, "recipientHandle is required");
            Objects.requireNonNull(actorDisplayName, "actorDisplayName is required");
            Objects.requireNonNull(pageTitle, "pageTitle is required");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        }
    }
}
