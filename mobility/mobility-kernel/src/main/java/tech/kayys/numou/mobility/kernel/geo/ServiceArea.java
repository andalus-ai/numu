package tech.kayys.numou.mobility.kernel.geo;

import java.util.List;

/** A circular operating area (a city or district). A pickup outside every area is refused: no drivers, no coverage, no support. */
public record ServiceArea(String name, LatLng center, double radiusMeters) {

    public ServiceArea {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }

    public boolean contains(LatLng p) {
        return center.distanceTo(p) <= radiusMeters;
    }

    public static boolean anyContains(List<ServiceArea> areas, LatLng p) {
        return areas.stream().anyMatch(a -> a.contains(p));
    }
}
