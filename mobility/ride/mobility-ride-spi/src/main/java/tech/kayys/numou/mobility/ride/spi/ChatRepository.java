package tech.kayys.numou.mobility.ride.spi;

import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.domain.RideChat;

public interface ChatRepository extends VersionedRepository<RideChat, RideId> { }
