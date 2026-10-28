package tech.kayys.numou.geo.domain;

import java.util.Objects;

/**
 * Exact spherical-radius predicate over WGS84 points.
 *
 * <p>The radius is expressed in metres and evaluated using {@link GeoDistance}'s spherical
 * great-circle approximation. This is a geometry predicate, not a spatial index or a query over
 * stored locations. Callers that retrieve candidates from a grid or database must ensure candidate
 * retrieval is complete, then apply this predicate to remove false positives.</p>
 */
public record GeoRadius(GeoPoint center, double radiusMeters) {

    public GeoRadius {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative metres");
        }
        // Canonicalize signed zero for stable value semantics.
        if (radiusMeters == 0.0) {
            radiusMeters = 0.0;
        }
    }

    /** Returns the spherical great-circle distance from the center in metres. */
    public double distanceTo(GeoPoint point) {
        return GeoDistance.metersBetween(center, Objects.requireNonNull(point, "point"));
    }

    /** Returns true when the point is at or inside the radius, including the boundary. */
    public boolean contains(GeoPoint point) {
        return distanceTo(point) <= radiusMeters;
    }
}
