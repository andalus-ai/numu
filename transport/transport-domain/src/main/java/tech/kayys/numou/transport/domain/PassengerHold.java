package tech.kayys.numou.transport.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record for one passenger-capacity claim.
 *
 * <p>The segment is half-open: {@code fromSequence} is included and
 * {@code toSequence} is excluded. A passenger travelling stop 1 to stop 3
 * therefore consumes capacity on legs 1-2 and 2-3.</p>
 */
public record PassengerHold(
        UUID id,
        String idempotencyKey,
        String requestFingerprint,
        int fromSequence,
        int toSequence,
        int passengerCount,
        Instant expiresAt,
        PassengerHoldStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public PassengerHold {
        Objects.requireNonNull(id, "id");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        requestFingerprint = requireText(requestFingerprint, "requestFingerprint");
        if (fromSequence < 1 || toSequence <= fromSequence) {
            throw new IllegalArgumentException("segment must have fromSequence >= 1 and toSequence > fromSequence");
        }
        if (passengerCount < 1) {
            throw new IllegalArgumentException("passengerCount must be positive");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (expiresAt.isBefore(createdAt) || expiresAt.equals(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public boolean overlaps(int from, int to) {
        return fromSequence < to && from < toSequence;
    }

    public boolean consumesCapacityAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return status == PassengerHoldStatus.CONFIRMED
                || (status == PassengerHoldStatus.HELD && expiresAt.isAfter(now));
    }

    PassengerHold transition(PassengerHoldStatus next, Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("transition time must not be before updatedAt");
        }
        return new PassengerHold(id, idempotencyKey, requestFingerprint, fromSequence, toSequence,
                passengerCount, expiresAt, next, createdAt, now);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
