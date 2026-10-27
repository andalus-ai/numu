package tech.kayys.numou.geo.domain;

import java.util.Locale;

/**
 * A WGS84 latitude/longitude point in decimal degrees.
 *
 * <p>Coordinates are precise location data. The default string representation deliberately rounds
 * coordinates and must not be used as a serialization format or stable identifier.</p>
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be finite and within [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be finite and within [-180, 180]");
        }
        // Canonicalize signed zero so equivalent coordinates compare consistently.
        if (latitude == 0.0) latitude = 0.0;
        if (longitude == 0.0) longitude = 0.0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "GeoPoint[~%.2f,~%.2f]", latitude, longitude);
    }
}
