package tech.kayys.numou.geo.domain;

import java.util.List;
import java.util.Objects;

/** Spherical-earth calculations for approximate geographic distances, not road-route estimates. */
public final class GeoDistance {

    /** Mean Earth radius in metres (IUGG mean-radius convention). */
    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {
    }

    /**
     * Returns great-circle distance in metres using the haversine formula.
     * This is an approximation on a sphere and must not be presented as road distance or ETA.
     */
    public static double metersBetween(GeoPoint from, GeoPoint to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        double latitude1 = Math.toRadians(from.latitude());
        double latitude2 = Math.toRadians(to.latitude());
        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
        double sinLatitude = Math.sin(deltaLatitude / 2.0);
        double sinLongitude = Math.sin(deltaLongitude / 2.0);
        double haversine = sinLatitude * sinLatitude
                + Math.cos(latitude1) * Math.cos(latitude2) * sinLongitude * sinLongitude;
        // Clamp for floating-point round-off near antipodal points.
        double bounded = Math.max(0.0, Math.min(1.0, haversine));
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(bounded));
    }

    /** Sum great-circle segment lengths for a path; an empty or one-point path has zero length. */
    public static double pathLengthMeters(List<GeoPoint> points) {
        Objects.requireNonNull(points, "points");
        // Validate every element, including the single-point path case where the segment loop
        // would otherwise never dereference the point.
        for (GeoPoint point : points) {
            Objects.requireNonNull(point, "path point");
        }
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += metersBetween(points.get(index - 1), points.get(index));
        }
        return total;
    }
}
