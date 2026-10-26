package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reservation.domain.Reservation;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional extension for reservation stores that can atomically persist an idempotency key
 * with a reservation. Keys are scoped by tenant and request fingerprints must be compared
 * using a stable canonical representation supplied by the caller.
 *
 * <p>Production implementations MUST commit the key, reservation, and reservation-side
 * allocation in the same transaction/unit of work. A check-then-save implementation is
 * not compliant because concurrent retries can create duplicate bookings.</p>
 */
public interface IdempotentReservationRepository extends ReservationRepository {
    /**
     * Finds the reservation committed for this tenant-scoped key.
     */
    Uni<Optional<Reservation>> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    /**
     * Atomically saves the reservation and binds the key to the request fingerprint.
     *
     * <ul>
     *   <li>If the key is unused, persists the reservation and returns it.</li>
     *   <li>If the key was used for the same fingerprint, returns the original reservation.</li>
     *   <li>If the key was used for a different fingerprint, fails with
     *       {@link IdempotencyKeyConflictException}.</li>
     * </ul>
     */
    Uni<Reservation> saveIdempotently(UUID tenantId, String idempotencyKey,
                                      String requestFingerprint, Reservation candidate);

    /** Raised when a key is reused for a semantically different command. */
    final class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String message) {
            super(message);
        }
    }
}
