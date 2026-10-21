package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.domain.TracePoint;

import java.util.List;
import java.util.UUID;

/**
 * Append-only GPS trace of a trip: the evidence for fare disputes and safety incidents, and the source of the final distance.
 * Insert-only on purpose: one small row per accepted fix instead of rewriting the whole ride document.
 */
public interface TripTraceRepository {

    Uni<Void> append(UUID tenantId, RideId rideId, List<TracePoint> points);

    /** Time-ordered. */
    Uni<List<TracePoint>> load(UUID tenantId, RideId rideId);
}
