package tech.kayys.numou.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recipient-facing notification record. Delivery mechanics and business events
 * belong to other capabilities; this model owns inbox content and read state.
 */
public record Notification(
        UUID id,
        UUID tenantId,
        UUID recipientId,
        String type,
        String title,
        String body,
        String sourceReference,
        String idempotencyKey,
        Instant createdAt,
        Instant readAt) {

    public static final int MAX_TYPE_LENGTH = 120;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 4000;
    public static final int MAX_SOURCE_REFERENCE_LENGTH = 500;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    public Notification {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
        type = required(type, "type", MAX_TYPE_LENGTH);
        title = required(title, "title", MAX_TITLE_LENGTH);
        body = required(body, "body", MAX_BODY_LENGTH);
        sourceReference = optional(sourceReference, MAX_SOURCE_REFERENCE_LENGTH, "sourceReference");
        idempotencyKey = required(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (readAt != null && readAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("readAt cannot precede createdAt");
        }
    }

    public static Notification create(
            UUID tenantId,
            UUID recipientId,
            String type,
            String title,
            String body,
            String sourceReference,
            String idempotencyKey,
            Instant now) {
        return new Notification(UUID.randomUUID(), tenantId, recipientId, type, title, body,
                sourceReference, idempotencyKey, now, null);
    }

    /** Reading is idempotent: an already-read notification remains unchanged. */
    public Notification markRead(Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("read time cannot precede creation time");
        }
        return readAt == null
                ? new Notification(id, tenantId, recipientId, type, title, body,
                    sourceReference, idempotencyKey, createdAt, now)
                : this;
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String required(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}