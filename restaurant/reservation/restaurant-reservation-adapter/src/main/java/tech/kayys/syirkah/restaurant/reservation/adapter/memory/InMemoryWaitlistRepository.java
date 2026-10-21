package tech.kayys.syirkah.restaurant.reservation.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kernel.domain.WaitlistEntryId;
import tech.kayys.syirkah.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.WaitlistEntry;
import tech.kayys.syirkah.restaurant.reservation.spi.WaitlistRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InMemoryWaitlistRepository
        extends InMemoryDocumentRepository<WaitlistEntry, WaitlistEntryId, WaitlistEntry.Snapshot>
        implements WaitlistRepository {

    public InMemoryWaitlistRepository() {
        super(WaitlistEntry::snapshot, WaitlistEntry::restore);
    }

    @Override
    public Uni<List<WaitlistEntry>> findOpen(UUID tenantId, VenueId venueId) {
        return Uni.createFrom().item(() -> allOf(tenantId).stream()
                .filter(e -> e.venueId().equals(venueId)
                        && (e.status() == WaitlistEntry.Status.WAITING || e.status() == WaitlistEntry.Status.NOTIFIED))
                .sorted(Comparator.comparing(WaitlistEntry::joinedAt))
                .toList());
    }
}
