package tech.kayys.numou.workspace.adapter.notification;

import tech.kayys.numou.notification.application.NotificationService;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionDeliveryPort;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.ClaimedMention;

import java.util.Objects;

/**
 * Optional integration adapter that turns a durable Workspace mention into an
 * idempotent Notification inbox record. The outbox dispatcher owns retries;
 * NotificationRepository owns recipient-scoped idempotency.
 */
public final class NotificationWorkspaceMentionDeliveryAdapter implements WorkspaceMentionDeliveryPort {
    private static final String NOTIFICATION_TYPE = "workspace.comment.mention";
    private static final String BODY = "You were mentioned in a comment.";

    private final NotificationService notifications;

    public NotificationWorkspaceMentionDeliveryAdapter(NotificationService notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications is required");
    }

    @Override
    public void deliver(ClaimedMention mention) {
        Objects.requireNonNull(mention, "mention is required");
        String title = title(mention.actorDisplayName(), mention.pageTitle());
        String sourceReference = "workspace://pages/" + mention.pageId()
                + "/comments/" + mention.commentId();
        notifications.create(new NotificationService.CreateNotification(
                mention.tenantId(),
                mention.recipientId(),
                NOTIFICATION_TYPE,
                title,
                BODY,
                sourceReference,
                mention.idempotencyKey()));
    }

    private static String title(String actorDisplayName, String pageTitle) {
        String actor = required(actorDisplayName, "actorDisplayName");
        String page = pageTitle == null || pageTitle.isBlank() ? "a page" : pageTitle.trim();
        String value = actor + " mentioned you in " + page;
        if (value.length() <= 200) return value;
        return value.substring(0, 197) + "...";
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
