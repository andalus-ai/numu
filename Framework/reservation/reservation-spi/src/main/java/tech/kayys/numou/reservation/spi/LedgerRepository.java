package tech.kayys.syirkah.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reservation.domain.AvailabilityLedger;
import tech.kayys.syirkah.reservation.domain.FacilityId;
import tech.kayys.syirkah.reservation.domain.LedgerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {
    Uni<Optional<AvailabilityLedger>> find(UUID tenantId, LedgerId id);
    Uni<Void> save(UUID tenantId, AvailabilityLedger ledger);

    default Uni<AvailabilityLedger> findOrCreate(UUID tenantId, FacilityId facilityId, LocalDate day, Instant now) {
        return find(tenantId, LedgerId.forFacilityDate(facilityId, day))
                .map(opt -> opt.orElseGet(() -> AvailabilityLedger.open(facilityId, day, now)));
    }
}
