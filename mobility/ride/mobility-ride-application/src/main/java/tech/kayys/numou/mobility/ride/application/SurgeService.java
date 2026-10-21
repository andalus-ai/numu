package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.pricing.SurgePolicy;
import tech.kayys.numou.mobility.ride.spi.DemandCounter;
import tech.kayys.numou.mobility.ride.spi.SurgeStateStore;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Demand/supply surge for the area around a pickup. Demand is ride requests in the last 5 minutes within ~1.5 km; supply is
 * available drivers of that vehicle type within the same radius. The multiplier moves toward the target at most one step per
 * 30 seconds and the value in force is stored, so concurrent quotes in a cell all see the same price.
 */
public final class SurgeService {

    private static final double RADIUS_M = 1_500;
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration STEP_EVERY = Duration.ofSeconds(30);

    private final DemandCounter demand;
    private final DriverLocationIndex drivers;
    private final SurgeStateStore state;
    private final SurgePolicy policy;
    private final DomainClock clock;

    public SurgeService(DemandCounter demand, DriverLocationIndex drivers, SurgeStateStore state, SurgePolicy policy, DomainClock clock) {
        this.demand = demand;
        this.drivers = drivers;
        this.state = state;
        this.policy = policy;
        this.clock = clock;
    }

    public Uni<BigDecimal> multiplierFor(UUID tenantId, LatLng pickup, VehicleType vehicle) {
        final var now = clock.now();
        final var cell = GeoCell.of(pickup);
        return state.get(tenantId, cell).flatMap(current -> {
            if (current.isPresent() && Duration.between(current.get().updatedAt(), now).compareTo(STEP_EVERY) < 0) {
                return Uni.createFrom().item(current.get().multiplier());          // inside the step interval: everyone sees one price
            }
            return demand.count(tenantId, GeoCell.around(pickup, RADIUS_M), now.minus(WINDOW)).flatMap(d ->
                    drivers.nearby(tenantId, pickup, RADIUS_M, vehicle, now.minusSeconds(30), 500).flatMap(near -> {
                        final BigDecimal target = policy.target(d, near.size());
                        final BigDecimal base = current.map(SurgeStateStore.State::multiplier).orElse(new BigDecimal("1.0"));
                        final BigDecimal next = policy.next(base, target);
                        return state.put(tenantId, cell, new SurgeStateStore.State(next, now)).replaceWith(next);
                    }));
        });
    }

    public Uni<Void> recordRequest(UUID tenantId, LatLng pickup) {
        return demand.record(tenantId, GeoCell.of(pickup), clock.now());
    }
}
