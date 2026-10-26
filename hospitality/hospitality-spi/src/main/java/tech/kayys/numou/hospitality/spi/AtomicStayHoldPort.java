package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import tech.kayys.numou.hospitality.domain.StayBooking;

/**
 * Atomically reserves capacity for every night in [checkIn, checkOut) and durably records
 * the booking/hold result. Must enforce uniqueness of tenant/customer/idempotencyKey.
 * A failed or timed-out call must be safe to retry with the same key. Never implement as
 * a read-check-write sequence; use a transaction or durable recoverable protocol.
 */
public interface AtomicStayHoldPort {
    Uni<StayBooking> createHoldAtomically(StayBooking booking, Instant holdExpiresAt);
}
