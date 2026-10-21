package tech.kayys.numou.mobility.kernel.geo;

import java.util.Objects;

/** A pickup or drop-off: a coordinate plus a human label. The label is an address (personal data) and is never logged. */
public record Place(LatLng point, String label) {

    public Place {
        Objects.requireNonNull(point, "point cannot be null");
        label = label == null ? "" : (label.length() > 200 ? label.substring(0, 200) : label);
    }

    @Override
    public String toString() {
        return "Place[" + point + "]";
    }
}
