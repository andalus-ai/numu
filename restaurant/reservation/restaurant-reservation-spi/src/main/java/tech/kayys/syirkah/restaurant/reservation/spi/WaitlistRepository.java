package tech.kayys.syirkah.restaurant.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.domain.WaitlistEntryId;
import tech.kayys.syirkah.aggregate.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.WaitlistEntry;

import java.util.List;
import java.util.UUID;

public interface WaitlistRepository extends VersionedRepository<WaitlistEntry, WaitlistEntryId> {

    /** Entries still WAITING or NOTIFIED, oldest first. */
    Uni<List<WaitlistEntry>> findOpen(UUID tenantId, VenueId venueId);
}
