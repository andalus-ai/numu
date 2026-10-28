package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.domain.Notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application use cases for the in-app inbox. Authentication and tenant
 * selection are supplied by the hosting adapter; callers must not treat
 * request-provided identifiers as proof of authorization.
 */
public final class NotificationService {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public Notification create(CreateNotification command) {
        Objects.requireNonNull(command, "command is required");
        Notification notification = Notification.create(
                command.tenantId(), command.recipientId(), command.type(), command.title(),
                command.body(), command.sourceReference(), command.idempotencyKey(), Instant.now(clock));
        return repository.createIfAbsent(notification);
    }

    public List<Notification> inbox(UUID tenantId, UUID recipientId, int requestedLimit, Instant beforeCreatedAt) {
        requireScope(tenantId, recipientId);
        int limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);
        return List.copyOf(repository.findInbox(tenantId, recipientId, limit, beforeCreatedAt));
    }

    public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId) {
        requireScope(tenantId, recipientId);
        Objects.requireNonNull(notificationId, "notificationId is required");
        return repository.markRead(tenantId, recipientId, notificationId, Instant.now(clock));
    }

    private static void requireScope(UUID tenantId, UUID recipientId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
    }

    public record CreateNotification(
            UUID tenantId,
            UUID recipientId,
            String type,
            String title,
            String body,
            String sourceReference,
            String idempotencyKey) {
        public CreateNotification {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(recipientId, "recipientId is required");
            Objects.requireNonNull(type, "type is required");
            Objects.requireNonNull(title, "title is required");
            Objects.requireNonNull(body, "body is required");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        }
    }
}