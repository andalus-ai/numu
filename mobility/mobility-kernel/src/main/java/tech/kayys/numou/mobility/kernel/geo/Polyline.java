package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.geo.domain.GeoPolylineCodec;

import java.util.List;

/**
 * Mobility compatibility facade for the shared precision-5 encoded polyline codec.
 *
 * <p>The public LatLng API remains stable while the wire-format implementation is owned by Geo,
 * so other Numou capabilities can use the same deterministic encoding without depending on Mobility.</p>
 */
public final class Polyline {

    private Polyline() {
    }

    public static String encode(List<LatLng> points) {
        return GeoPolylineCodec.encode(points.stream().map(LatLng::toGeoPoint).toList());
    }

    public static List<LatLng> decode(String encoded) {
        return GeoPolylineCodec.decode(encoded).stream().map(LatLng::fromGeoPoint).toList();
    }
}
