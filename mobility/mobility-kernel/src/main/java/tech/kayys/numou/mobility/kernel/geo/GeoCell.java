package tech.kayys.numou.mobility.kernel.geo;

import tech.kayys.numou.geo.domain.GeoGrid;
import tech.kayys.numou.geo.domain.GeoGridCell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A fixed 0.01 degree grid cell (~1.1 km tall; narrower in longitude away from the equator). Cells are the unit of the
 * driver index and of surge zones: "drivers near here" becomes a lookup of a few cell keys instead of a scan.
 */
public record GeoCell(int latIndex, int lngIndex) {

    public static final double CELL_DEGREES = 0.01;
    private static final int MIN_LAT_INDEX = -9_000;
    private static final int MAX_LAT_INDEX = 9_000;
    private static final int MIN_LNG_INDEX = -18_000;
    private static final int MAX_LNG_INDEX = 18_000;
    private static final int LONGITUDE_CELL_COUNT = 36_000;
    private static final int MAX_CANDIDATE_CELLS = 1_000_000;

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
     * Conservative candidate cells for a spherical radius. Handles longitude wrap at the antimeridian and uses every
     * longitude when the search reaches a pole. Results are only candidates: callers must apply exact distance filtering.
     *
     * @throws IllegalArgumentException for invalid radii or a search whose candidate set would exceed the safety limit
     */
    public static List<GeoCell> around(LatLng center, double radiusMeters) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative metres");
        }

        final double dLat = radiusMeters / GeoMath.METERS_PER_DEGREE_LAT;
        final double south = Math.max(-90.0, center.lat() - dLat);
        final double north = Math.min(90.0, center.lat() + dLat);
        final int minLat = Math.max(MIN_LAT_INDEX, (int) Math.floor(south / CELL_DEGREES));
        final int maxLat = Math.min(MAX_LAT_INDEX, (int) Math.floor(north / CELL_DEGREES));

        final boolean reachesPole = center.lat() - dLat <= -90.0 || center.lat() + dLat >= 90.0;
        final double maxAbsLatitude = Math.max(Math.abs(south), Math.abs(north));
        final double cos = Math.cos(Math.toRadians(maxAbsLatitude));
        final double dLng = reachesPole || cos <= 0.0
                ? 180.0
                : radiusMeters / (GeoMath.METERS_PER_DEGREE_LAT * cos);
        final boolean allLongitudes = reachesPole || dLng >= 180.0;
        final LinkedHashSet<Integer> longitudes = new LinkedHashSet<>();
        if (allLongitudes) {
            for (int lng = MIN_LNG_INDEX; lng < MAX_LNG_INDEX; lng++) {
                longitudes.add(lng);
            }
            // Longitude +180 is a valid legacy coordinate and has its own historical cell index.
            longitudes.add(MAX_LNG_INDEX);
        } else {
            final int rawMin = (int) Math.floor((center.lng() - dLng) / CELL_DEGREES);
            final int rawMax = (int) Math.floor((center.lng() + dLng) / CELL_DEGREES);
            for (int raw = rawMin; raw <= rawMax; raw++) {
                int normalized = raw;
                while (normalized < MIN_LNG_INDEX) normalized += LONGITUDE_CELL_COUNT;
                while (normalized >= MAX_LNG_INDEX) normalized -= LONGITUDE_CELL_COUNT;
                longitudes.add(normalized);
                // The two valid coordinate representations of the antimeridian must share candidate coverage.
                if (normalized == MIN_LNG_INDEX) longitudes.add(MAX_LNG_INDEX);
                if (normalized == MAX_LNG_INDEX - 1 && rawMax >= MAX_LNG_INDEX) longitudes.add(MIN_LNG_INDEX);
            }
        }

        final long estimatedCells = (long) (maxLat - minLat + 1) * longitudes.size();
        if (estimatedCells > MAX_CANDIDATE_CELLS) {
            throw new IllegalArgumentException("radius produces too many candidate cells; use a spatial query adapter");
        }

        final var cells = new ArrayList<GeoCell>((int) estimatedCells);
        for (int lat = minLat; lat <= maxLat; lat++) {
            for (int lng : longitudes) {
                cells.add(new GeoCell(lat, lng));
            }
        }
        return List.copyOf(cells);
    }
}
