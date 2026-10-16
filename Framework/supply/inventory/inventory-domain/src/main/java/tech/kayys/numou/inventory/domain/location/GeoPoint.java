package tech.kayys.numou.inventory.domain.location;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Geographic coordinates representation.
 */
public record GeoPoint(
        BigDecimal latitude,
        BigDecimal longitude
) {
    public GeoPoint {
        Objects.requireNonNull(latitude, "latitude cannot be null");
        Objects.requireNonNull(longitude, "longitude cannot be null");

        if (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90: " + latitude);
        }

        if (longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180: " + longitude);
        }
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}
