package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Date-range availability boundary. Implementations must interpret check-out as exclusive
 * and consider every night in [checkIn, checkOut). This is a read-only check; booking must
 * use the atomic hold operation rather than relying on this result to prevent overselling.
 */
public interface StayAvailabilityPort {
    Uni<Boolean> isAvailable(String tenantId, UUID listingId, UUID unitId,
                             LocalDate checkIn, LocalDate checkOut, int guestCount);
}
