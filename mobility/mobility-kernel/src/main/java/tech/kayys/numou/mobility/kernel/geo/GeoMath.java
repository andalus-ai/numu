package tech.kayys.numou.mobility.kernel.geo;

import java.util.List;

/** Spherical-earth geometry. Accurate to ~0.5% - ample for dispatch, geofences and distance accumulation. */
public final class GeoMath {

    public static final double EARTH_RADIUS_M = 6_371_008.8;
    public static final double METERS_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_M / 180.0;

    private GeoMath() { }

    public static double haversineMeters(LatLng a, LatLng b) {
        final double p1 = Math.toRadians(a.lat());
        final double p2 = Math.toRadians(b.lat());
        final double dp = p2 - p1;
        final double dl = Math.toRadians(b.lng() - a.lng());
        final double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    public static double pathLengthMeters(List<LatLng> path) {
        double total = 0;
        for (int i = 1; i < path.size(); i++) {
            total += haversineMeters(path.get(i - 1), path.get(i));
        }
        return total;
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
