package tech.kayys.numou.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable per-device delivery attempt state. Error codes must be non-sensitive. */
public record NotificationDeliveryJob(
        UUID id, UUID tenantId, UUID recipientId, UUID notificationId, UUID deviceId,
        String provider, String idempotencyKey, Status status, int attemptCount,
        Instant createdAt, Instant nextAttemptAt, UUID leaseToken, Instant leaseUntil,
        Instant deliveredAt, String lastErrorCode) {

    public enum Status { PENDING, IN_FLIGHT, RETRY_WAIT, DELIVERED, DEAD }
    public static final int MAX_PROVIDER_LENGTH = 40;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 240;
    public static final int MAX_ERROR_CODE_LENGTH = 120;

    public NotificationDeliveryJob {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
        Objects.requireNonNull(notificationId, "notificationId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        provider = required(provider, "provider", MAX_PROVIDER_LENGTH);
        idempotencyKey = required(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
        Objects.requireNonNull(status, "status is required");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt is required");
        lastErrorCode = optional(lastErrorCode, MAX_ERROR_CODE_LENGTH);
        if (status == Status.IN_FLIGHT && (leaseToken == null || leaseUntil == null))
            throw new IllegalArgumentException("in-flight jobs require a lease token and expiry");
        if (status != Status.IN_FLIGHT && (leaseToken != null || leaseUntil != null))
            throw new IllegalArgumentException("only in-flight jobs may hold a lease");
        if (status == Status.DELIVERED && deliveredAt == null)
            throw new IllegalArgumentException("delivered jobs require deliveredAt");
        if (status != Status.DELIVERED && deliveredAt != null)
            throw new IllegalArgumentException("only delivered jobs may have deliveredAt");
        if (leaseUntil != null && !leaseUntil.isAfter(createdAt))
            throw new IllegalArgumentException("lease expiry must be after creation");
        if (deliveredAt != null && deliveredAt.isBefore(createdAt))
            throw new IllegalArgumentException("deliveredAt cannot precede createdAt");
    }

    public static NotificationDeliveryJob pending(
            UUID tenantId, UUID recipientId, UUID notificationId, UUID deviceId,
            String provider, String idempotencyKey, Instant now) {
        return new NotificationDeliveryJob(UUID.randomUUID(), tenantId, recipientId,
                notificationId, deviceId, provider, idempotencyKey, Status.PENDING, 0,
                now, now, null, null, null, "");
    }

    public NotificationDeliveryJob claim(UUID token, Instant leaseExpiry, Instant now) {
        Objects.requireNonNull(token, "token is required");
        Objects.requireNonNull(leaseExpiry, "leaseExpiry is required");
        Objects.requireNonNull(now, "now is required");
        if (status != Status.PENDING && status != Status.RETRY_WAIT && status != Status.IN_FLIGHT)
            throw new IllegalStateException("terminal delivery job cannot be claimed");
        if (!leaseExpiry.isAfter(now)) throw new IllegalArgumentException("lease expiry must be after now");
        if (status == Status.IN_FLIGHT && leaseUntil.isAfter(now))
            throw new IllegalStateException("delivery job lease is still active");
        if (status != Status.IN_FLIGHT && nextAttemptAt.isAfter(now))
            throw new IllegalStateException("delivery job is not due");
        return new NotificationDeliveryJob(id, tenantId, recipientId, notificationId, deviceId,
                provider, idempotencyKey, Status.IN_FLIGHT, Math.addExact(attemptCount, 1),
                createdAt, nextAttemptAt, token, leaseExpiry, null, lastErrorCode);
    }

    public NotificationDeliveryJob delivered(UUID token, Instant now) {
        requireLease(token);
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(createdAt)) throw new IllegalArgumentException("delivery time precedes creation");
        return new NotificationDeliveryJob(id, tenantId, recipientId, notificationId, deviceId,
                provider, idempotencyKey, Status.DELIVERED, attemptCount, createdAt, nextAttemptAt,
                null, null, now, "");
    }

    public NotificationDeliveryJob retry(UUID token, Instant retryAt, String errorCode) {
        requireLease(token);
        Objects.requireNonNull(retryAt, "retryAt is required");
        if (retryAt.isBefore(createdAt)) throw new IllegalArgumentException("retry time precedes creation");
        return new NotificationDeliveryJob(id, tenantId, recipientId, notificationId, deviceId,
                provider, idempotencyKey, Status.RETRY_WAIT, attemptCount, createdAt, retryAt,
                null, null, null, errorCode);
    }

    public NotificationDeliveryJob dead(UUID token, String errorCode) {
        requireLease(token);
        return new NotificationDeliveryJob(id, tenantId, recipientId, notificationId, deviceId,
                provider, idempotencyKey, Status.DEAD, attemptCount, createdAt, nextAttemptAt,
                null, null, null, errorCode);
    }

    private void requireLease(UUID token) {
        Objects.requireNonNull(token, "token is required");
        if (status != Status.IN_FLIGHT || !token.equals(leaseToken))
            throw new IllegalStateException("delivery job lease is not owned by this worker");
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("error code is too long");
        return normalized;
    }
}
