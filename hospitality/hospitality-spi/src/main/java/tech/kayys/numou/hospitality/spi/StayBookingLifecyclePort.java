package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import tech.kayys.numou.hospitality.domain.StayBooking;

/**
 * Durable, tenant-scoped lifecycle operations for stay bookings.
 * Implementations must commit each state transition atomically so inventory occupancy
 * changes with the persisted status, and must make retries safe.
 */
public interface StayBookingLifecyclePort {
    /** Confirms a held booking; an expired hold is durably marked EXPIRED instead. */
    Uni<Optional<StayBooking>> confirm(String tenantId, UUID bookingId, Instant now);

    /** Cancels a held or confirmed booking; a hold past its deadline becomes EXPIRED. */
    Uni<Optional<StayBooking>> cancel(String tenantId, UUID bookingId, Instant now);

    /**
     * Expires a bounded batch of overdue holds. Concurrent workers must safely claim disjoint
     * rows, and retries must not transition already-terminal bookings again.
     */
    Uni<Integer> expireDue(Instant now, int batchSize);
}
