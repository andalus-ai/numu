package tech.kayys.numou.geo.domain;

import java.util.Objects;

/**
 * Configurable angular grid for coarse spatial bucketing.
 *
 * <p>This is a candidate-index primitive, not a distance metric or a complete spatial index.
 * Cell dimensions vary in metres with latitude; callers must filter candidates with exact
 * geographic predicates and explicitly handle antimeridian/polar search behavior.</p>
 */
public final class GeoGrid {

    private static final double MIN_LONG_INDEX = -0x1.0p63;
    private static final double MAX_LONG_INDEX_EXCLUSIVE = 0x1.0p63;

    private GeoGrid() {
    }

    /** Returns the angular grid cell containing {@code point}. */
    public static GeoGridCell cellOf(GeoPoint point, double cellSizeDegrees) {
        Objects.requireNonNull(point, "point");
        validateCellSize(cellSizeDegrees);
        return new GeoGridCell(
                cellSizeDegrees,
                index(point.latitude(), cellSizeDegrees),
                index(point.longitude(), cellSizeDegrees));
    }

    private static long index(double coordinate, double cellSizeDegrees) {
        double floored = Math.floor(coordinate / cellSizeDegrees);
        if (!Double.isFinite(floored)
                || floored < MIN_LONG_INDEX
                || floored >= MAX_LONG_INDEX_EXCLUSIVE) {
            throw new IllegalArgumentException("cell size is too small for a stable grid index");
        }
        return (long) floored;
    }

    private static void validateCellSize(double cellSizeDegrees) {
        if (!Double.isFinite(cellSizeDegrees) || cellSizeDegrees <= 0.0 || cellSizeDegrees > 360.0) {
            throw new IllegalArgumentException("cell size must be finite and in (0, 360] degrees");
        }
    }
}
