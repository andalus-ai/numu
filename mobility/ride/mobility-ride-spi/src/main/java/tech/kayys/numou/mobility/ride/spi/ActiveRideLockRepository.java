package tech.kayys.numou.mobility.ride.spi;

import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.ride.domain.ActiveRideLock;

public interface ActiveRideLockRepository extends VersionedRepository<ActiveRideLock, RiderId> { }
