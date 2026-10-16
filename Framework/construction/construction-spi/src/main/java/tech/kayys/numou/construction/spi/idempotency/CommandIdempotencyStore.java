package tech.kayys.syirkah.construction.spi.idempotency;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage contract for retry-safe command execution.
 *
 * <p>Reservation must be protected by a unique key on (tenantId, key). Reusing
 * a key with a different operation or request fingerprint is a conflict. The
 * final outcome must be committed in the same transaction as the command's
 * business writes.</p>
 */
public interface CommandIdempotencyStore {
    Uni<ReservationResult> reserve(Reservation reservation);

    Uni<Boolean> complete(String tenantId, String key, String requestFingerprint,
                          String responseJson, Instant completedAt);

    Uni<Optional<StoredOutcome>> find(String tenantId, String key);

    record Reservation(String tenantId, String key, String operation,
                       String requestFingerprint, Instant createdAt) {
        public Reservation {
            if (tenantId == null || tenantId.isBlank() || key == null || key.isBlank()
                    || operation == null || operation.isBlank()
                    || requestFingerprint == null || requestFingerprint.isBlank()
                    || createdAt == null) {
                throw new IllegalArgumentException("Invalid idempotency reservation");
            }
        }
    }

    enum ReservationStatus { RESERVED, EXISTING_MATCH, KEY_REUSED_WITH_DIFFERENT_REQUEST }

    record ReservationResult(ReservationStatus status, Optional<StoredOutcome> existing) {
        public ReservationResult {
            if (status == null) throw new IllegalArgumentException("Status cannot be null");
            existing = existing == null ? Optional.empty() : existing;
        }
    }

    record StoredOutcome(String operation, String requestFingerprint,
                         String state, String responseJson) {}
}
