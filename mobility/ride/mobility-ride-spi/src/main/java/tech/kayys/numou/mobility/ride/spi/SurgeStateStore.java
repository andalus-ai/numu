package tech.kayys.numou.mobility.ride.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The multiplier currently in force for a cell, so surge ramps in steps instead of jumping on every quote. */
public interface SurgeStateStore {

    record State(BigDecimal multiplier, Instant updatedAt) { }

    Uni<Optional<State>> get(UUID tenantId, GeoCell cell);

    Uni<Void> put(UUID tenantId, GeoCell cell, State state);
}
