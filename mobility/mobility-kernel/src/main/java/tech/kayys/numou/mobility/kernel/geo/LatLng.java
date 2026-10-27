package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.geo.domain.GeoPoint;

/** A WGS84 coordinate. Location is personal data: {@link #toString()} is rounded to ~1 km so it is safe in logs. */
public record LatLng(double lat, double lng) {

    public LatLng {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinate");
        }
    }

    /** Creates the Mobility compatibility type from the shared Geo domain primitive. */
    public static LatLng fromGeoPoint(GeoPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        return new LatLng(point.latitude(), point.longitude());
    }

    /** Converts this legacy Mobility coordinate to the shared Geo domain primitive without rounding. */
    public GeoPoint toGeoPoint() {
        return new GeoPoint(lat, lng);
    }

    public double distanceTo(LatLng other) {
        return GeoMath.haversineMeters(this, other);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "LatLng[~%.2f,%.2f]", lat, lng);
    }
}
