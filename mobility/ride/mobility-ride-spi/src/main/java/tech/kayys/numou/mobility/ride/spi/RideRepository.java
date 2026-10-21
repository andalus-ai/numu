package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.ride.domain.Ride;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository extends VersionedRepository<Ride, RideId> {

    /** Rides still looking for a driver (status SEARCHING); the dispatch sweeper's work list. */
    Uni<List<Ride>> findSearching(UUID tenantId, int limit);

    /** Newest first. */
    Uni<List<Ride>> history(UUID tenantId, RiderId riderId, int limit);

    /** The ride (if any) that currently has a live offer addressed to this driver: the driver app's "do I have an offer?" poll. */
    Uni<Optional<Ride>> findLiveOfferFor(UUID tenantId, UUID driverId);

    /** Finished rides whose money movements are not marked settled: the settlement safety net. */
    Uni<List<Ride>> findEndedUnsettled(UUID tenantId, int limit);
}
