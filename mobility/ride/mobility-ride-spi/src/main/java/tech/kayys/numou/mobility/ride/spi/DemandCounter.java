package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Ride requests per grid cell over a sliding window: the "demand" half of surge pricing. */
public interface DemandCounter {

    Uni<Void> record(UUID tenantId, GeoCell cell, Instant at);

    Uni<Integer> count(UUID tenantId, Collection<GeoCell> cells, Instant since);
}
