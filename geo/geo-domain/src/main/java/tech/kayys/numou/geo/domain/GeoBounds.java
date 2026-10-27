package tech.kayys.numou.geo.domain;

import java.util.Objects;

/**
 * Inclusive WGS84 rectangular bounds.
 *
 * <p>When west longitude is greater than east longitude, the rectangle crosses the
 * antimeridian (for example, 170 to -170 degrees). This type is a geographic filter, not a
 * substitute for a topology-aware polygon implementation.</p>
 */
public record GeoBounds(double south, double west, double north, double east) {

    public GeoBounds {
        if (!Double.isFinite(south) || !Double.isFinite(north)
                || south < -90.0 || north > 90.0 || south > north) {
            throw new IllegalArgumentException("latitude bounds must satisfy -90 <= south <= north <= 90");
        }
        if (!Double.isFinite(west) || !Double.isFinite(east)
                || west < -180.0 || west > 180.0 || east < -180.0 || east > 180.0) {
            throw new IllegalArgumentException("longitude bounds must be within [-180, 180]");
        }
    }

    public boolean contains(GeoPoint point) {
        Objects.requireNonNull(point, "point");
        boolean longitudeInside = west <= east
                ? point.longitude() >= west && point.longitude() <= east
                : point.longitude() >= west || point.longitude() <= east;
        return point.latitude() >= south && point.latitude() <= north && longitudeInside;
    }
}
