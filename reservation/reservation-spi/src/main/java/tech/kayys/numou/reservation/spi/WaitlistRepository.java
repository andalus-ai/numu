package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reservation.domain.FacilityId;
import tech.kayys.numou.reservation.domain.WaitlistEntry;
import tech.kayys.numou.reservation.domain.WaitlistEntryId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository {
    Uni<Optional<WaitlistEntry>> find(UUID tenantId, WaitlistEntryId id);
    Uni<Void> save(UUID tenantId, WaitlistEntry entry);
    Uni<List<WaitlistEntry>> findOpen(UUID tenantId, FacilityId facilityId);
}
