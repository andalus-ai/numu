package tech.kayys.numou.workspace.adapter.rest;

import tech.kayys.numou.notification.application.NotificationService;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionNotificationPort;

import java.util.Objects;

/**
 * Optional composition adapter from Workspace mentions to the shared Notification inbox.
 * It intentionally does not depend on push providers or delivery transports.
 */
public final class WorkspaceNotificationBridge implements WorkspaceMentionNotificationPort {
    private final NotificationService notifications;

    public WorkspaceNotificationBridge(NotificationService notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications is required");
    }

    @Override
    public void notifyMention(MentionNotification mention) {
        Objects.requireNonNull(mention, "mention is required");
        String title = mention.actorDisplayName() + " mentioned you";
        String body = "You were mentioned in a comment on " + mention.pageTitle();
        String source = "/workspace/pages/" + mention.pageId() + "?comment=" + mention.commentId();
        notifications.create(new NotificationService.CreateNotification(
                mention.tenantId(),
                mention.recipientId(),
                "workspace.mention",
                title,
                body,
                source,
                mention.idempotencyKey()));
    }
}
