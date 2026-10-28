package tech.kayys.numou.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered delivery endpoint owned by one recipient in one tenant.
 * The raw provider token is persistence-sensitive and must never be returned
 * by public management DTOs or written to logs.
 */
public record PushDevice(
        UUID id,
        UUID tenantId,
        UUID recipientId,
        String token,
        Platform platform,
        String appInstance,
        Instant createdAt,
        Instant updatedAt,
        Instant disabledAt) {

    public static final int MAX_TOKEN_LENGTH = 4096;
    public static final int MAX_APP_INSTANCE_LENGTH = 200;

    public PushDevice {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
        token = required(token, "token", MAX_TOKEN_LENGTH);
        Objects.requireNonNull(platform, "platform is required");
        appInstance = optional(appInstance, MAX_APP_INSTANCE_LENGTH);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
        if (disabledAt != null && disabledAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("disabledAt cannot precede createdAt");
        }
    }

    public static PushDevice register(
            UUID tenantId,
            UUID recipientId,
            String token,
            Platform platform,
            String appInstance,
            Instant now) {
        return new PushDevice(UUID.randomUUID(), tenantId, recipientId, token, platform,
                appInstance, now, now, null);
    }

    public PushDevice refresh(String newToken, Platform newPlatform, String newAppInstance, Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(createdAt) || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("device update time cannot move backwards");
        }
        return new PushDevice(id, tenantId, recipientId, newToken, newPlatform,
                newAppInstance, createdAt, now, null);
    }

    public PushDevice disable(Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(createdAt) || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("device disable time cannot move backwards");
        }
        if (disabledAt != null) {
            return this;
        }
        return new PushDevice(id, tenantId, recipientId, token, platform,
                appInstance, createdAt, now, now);
    }

    public boolean isActive() {
        return disabledAt == null;
    }

    public enum Platform {
        ANDROID,
        IOS,
        WEB
    }

    private static String required(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("appInstance exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
