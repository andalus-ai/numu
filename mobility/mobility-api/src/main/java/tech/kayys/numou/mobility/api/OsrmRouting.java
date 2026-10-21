package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.HaversineRouting;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.kernel.spi.RoutingPort;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * Road routing through an OSRM server, with a hard timeout and automatic fallback to the straight-line estimator: a slow or down
 * routing engine degrades quote accuracy, never availability. NOT COMPILED in the authoring sandbox.
 */
public final class OsrmRouting implements RoutingPort {

    private final WebClient client;
    private final URI base;
    private final Duration timeout;
    private final RoutingPort fallback = new HaversineRouting();

    public OsrmRouting(Vertx vertx, String baseUrl, Duration timeout) {
        this.client = WebClient.create(vertx);
        this.base = URI.create(baseUrl);
        this.timeout = timeout;
    }

    @Override
    public Uni<RouteEstimate> route(LatLng from, LatLng to, VehicleType vehicle) {
        final String path = String.format(Locale.ROOT, "/route/v1/driving/%.6f,%.6f;%.6f,%.6f", from.lng(), from.lat(), to.lng(), to.lat());
        return client.get(base.getPort() < 0 ? 80 : base.getPort(), base.getHost(), path).addQueryParam("overview", "false")
                .timeout(timeout.toMillis()).send()
                .map(resp -> {
                    final var route = resp.bodyAsJsonObject().getJsonArray("routes").getJsonObject(0);
                    // OSRM's driving profile is a car profile: scale durations for bikes (they filter through traffic)
                    final double factor = vehicle == VehicleType.BIKE ? 0.8 : 1.0;
                    return new RouteEstimate(route.getDouble("distance"), Math.round(route.getDouble("duration") * factor));
                })
                .onFailure().recoverWithUni(t -> fallback.route(from, to, vehicle));
    }
}
