package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.UUID;

/** Provider-side inventory capacity management for exclusive checkout date ranges. */
public interface StayInventoryProvisioningPort {
    Uni<Void> provision(String tenantId, UUID listingId, UUID unitId,
                        LocalDate checkIn, LocalDate checkOut, int capacity);
}
