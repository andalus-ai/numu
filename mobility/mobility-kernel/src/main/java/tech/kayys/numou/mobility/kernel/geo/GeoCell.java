package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.geo.domain.GeoGrid;
import tech.kayys.numou.geo.domain.GeoGridCell;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed 0.01 degree grid cell (~1.1 km tall; narrower in longitude away from the equator). Cells are the unit of the
 * driver index and of surge zones: "drivers near here" becomes a lookup of a few cell keys instead of a scan.
 */
public record GeoCell(int latIndex, int lngIndex) {

    public static final double CELL_DEGREES = 0.01;

    /** Uses Geo's generic bucketing math while preserving Mobility's index and key contract. */
    public static GeoCell of(LatLng p) {
        GeoGridCell cell = GeoGrid.cellOf(p.toGeoPoint(), CELL_DEGREES);
        return new GeoCell(Math.toIntExact(cell.latitudeIndex()), Math.toIntExact(cell.longitudeIndex()));
    }

    /** Stable text key, e.g. {@code -618:10682}; used as an indexed database column. */
    public String key() {
        return latIndex + ":" + lngIndex;
    }

    /**
     * Every cell that can contain a point within {@code radiusMeters} of {@code center}. The longitude span is widened
     * by 1/cos(latitude), so no point inside the radius is ever missed (callers still filter by exact distance).
     */
    public static List<GeoCell> around(LatLng center, double radiusMeters) {
        final double dLat = radiusMeters / GeoMath.METERS_PER_DEGREE_LAT;
        final double cos = Math.max(0.01, Math.cos(Math.toRadians(center.lat())));
        final double dLng = radiusMeters / (GeoMath.METERS_PER_DEGREE_LAT * cos);
        final int minLat = (int) Math.floor((center.lat() - dLat) / CELL_DEGREES);
        final int maxLat = (int) Math.floor((center.lat() + dLat) / CELL_DEGREES);
        final int minLng = (int) Math.floor((center.lng() - dLng) / CELL_DEGREES);
        final int maxLng = (int) Math.floor((center.lng() + dLng) / CELL_DEGREES);
        final var cells = new ArrayList<GeoCell>();
        for (int la = minLat; la <= maxLat; la++) {
            for (int ln = minLng; ln <= maxLng; ln++) {
                cells.add(new GeoCell(la, ln));
            }
        }
        return cells;
    }
}
