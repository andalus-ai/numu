package tech.kayys.syirkah.restaurant.reservation.spi;

import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;
import tech.kayys.syirkah.restaurant.reservation.domain.AvailabilityLedger;
import tech.kayys.syirkah.restaurant.reservation.domain.LedgerId;

public interface LedgerRepository extends VersionedRepository<AvailabilityLedger, LedgerId> { }
