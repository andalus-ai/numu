package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.geo.domain.GeoDistance;

import java.util.List;

/** Spherical-earth geometry. Accurate to ~0.5% - ample for dispatch, geofences and distance accumulation. */
public final class GeoMath {

    /** Retained for source compatibility; canonical value is owned by Geo. */
    public static final double EARTH_RADIUS_M = GeoDistance.EARTH_RADIUS_METERS;
    public static final double METERS_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_M / 180.0;

    private GeoMath() { }

    /** Delegates the shared great-circle calculation to the provider-neutral Geo domain. */
    public static double haversineMeters(LatLng a, LatLng b) {
        return GeoDistance.metersBetween(a.toGeoPoint(), b.toGeoPoint());
    }

    /** Delegates path validation and segment accumulation to the shared Geo domain. */
    public static double pathLengthMeters(List<LatLng> path) {
        return GeoDistance.pathLengthMeters(path.stream().map(LatLng::toGeoPoint).toList());
    }

    /** Point moved by the given metres north/east (small distances; used by tests and simulators). */
    public static LatLng offset(LatLng from, double northMeters, double eastMeters) {
        final double lat = from.lat() + northMeters / METERS_PER_DEGREE_LAT;
        final double lng = from.lng() + eastMeters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(from.lat())));
        return new LatLng(lat, lng);
    }

    /**
     * Shortest distance from {@code p} to the segment a-b, in metres. Uses a local flat projection around {@code a}, accurate to well
     * under 1% for the tens-of-kilometres distances of a city trip.
     */
    public static double distanceToSegmentMeters(LatLng p, LatLng a, LatLng b) {
        final double mLng = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(a.lat()));
        final double bx = (b.lng() - a.lng()) * mLng, by = (b.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        final double px = (p.lng() - a.lng()) * mLng, py = (p.lat() - a.lat()) * METERS_PER_DEGREE_LAT;
        final double len2 = bx * bx + by * by;
        final double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by) / len2));
        return Math.hypot(px - t * bx, py - t * by);
    }

    public static double kmh(double meters, double seconds) {
        return seconds <= 0 ? Double.POSITIVE_INFINITY : (meters / seconds) * 3.6;
    }
}
