package tech.kayys.numou.notification.application.port;

import tech.kayys.numou.notification.domain.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary. Implementations must enforce tenant scope and unique
 * idempotency on (tenantId, recipientId, idempotencyKey) transactionally.
 */
public interface NotificationRepository {
    /**
     * Stores a notification unless the same recipient-scoped idempotency key
     * already exists. Returns the canonical existing or newly stored record.
     */
    Notification createIfAbsent(Notification notification);

    List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant beforeCreatedAt);

    Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID notificationId);

    /** Atomic, recipient-scoped read transition; returns empty if not owned/found. */
    Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId, Instant readAt);
}