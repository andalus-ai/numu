package tech.kayys.numou.mobility.kernel.geo;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;

/**
 * Estimator used when no routing engine is configured or it is down: straight line x a detour factor at a typical
 * urban speed. Deliberately conservative (slightly long) so fares are not under-quoted.
 */
public final class HaversineRouting implements RoutingPort {

    private static final double DETOUR = 1.35;

    @Override
    public Uni<RouteEstimate> route(LatLng from, LatLng to, VehicleType vehicle) {
        return Uni.createFrom().item(() -> {
            final double meters = from.distanceTo(to) * DETOUR;
            final double kmh = vehicle == VehicleType.BIKE ? 28.0 : 22.0;
            return new RouteEstimate(meters, Math.round(meters / (kmh / 3.6)));
        });
    }
}
