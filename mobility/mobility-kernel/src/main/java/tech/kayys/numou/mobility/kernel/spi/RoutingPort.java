package tech.kayys.numou.mobility.kernel.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

/** Road distance/time between two points. Production: OSRM / Google / HERE adapter; fallback: {@code HaversineRouting}. */
public interface RoutingPort {

    record RouteEstimate(double distanceMeters, long durationSeconds) {
        public RouteEstimate {
            if (distanceMeters < 0 || durationSeconds < 0) {
                throw new IllegalArgumentException("Route values cannot be negative");
            }
        }
    }

    Uni<RouteEstimate> route(LatLng from, LatLng to, VehicleType vehicle);
}
