package tech.kayys.numou.geo.domain;

/**
 * A cell in a configurable angular {@link GeoGrid}.
 *
 * <p>Resolution is part of the identity and stable key: indices from different grids must not
 * collide. The grid is not equal-area and does not itself define neighbour/wrap semantics.</p>
 */
public record GeoGridCell(double cellSizeDegrees, long latitudeIndex, long longitudeIndex) {

    public GeoGridCell {
        if (!Double.isFinite(cellSizeDegrees) || cellSizeDegrees <= 0.0 || cellSizeDegrees > 360.0) {
            throw new IllegalArgumentException("cell size must be finite and in (0, 360] degrees");
        }
    }

    /** Stable key including resolution, suitable for coarse index keys but not for authorization. */
    public String key() {
        return Double.toString(cellSizeDegrees) + ":" + latitudeIndex + ":" + longitudeIndex;
    }
}
