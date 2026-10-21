package tech.kayys.numou.mobility.kernel.geo;

/** A WGS84 coordinate. Location is personal data: {@link #toString()} is rounded to ~1 km so it is safe in logs. */
public record LatLng(double lat, double lng) {

    public LatLng {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Invalid coordinate");
        }
    }

    public double distanceTo(LatLng other) {
        return GeoMath.haversineMeters(this, other);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "LatLng[~%.2f,%.2f]", lat, lng);
    }
}
