package tech.kayys.syirkah.restaurant.reservation.adapter.memory;

import tech.kayys.syirkah.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.AvailabilityLedger;
import tech.kayys.syirkah.restaurant.reservation.domain.LedgerId;
import tech.kayys.syirkah.restaurant.reservation.spi.LedgerRepository;

public class InMemoryLedgerRepository
        extends InMemoryDocumentRepository<AvailabilityLedger, LedgerId, AvailabilityLedger.Snapshot>
        implements LedgerRepository {

    public InMemoryLedgerRepository() {
        super(AvailabilityLedger::snapshot, AvailabilityLedger::restore);
    }
}
