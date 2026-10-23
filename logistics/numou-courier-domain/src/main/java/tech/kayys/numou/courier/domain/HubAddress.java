package tech.kayys.numou.courier.domain;

import java.util.Objects;

/**
 * Postal address for a hub. Country uses ISO 3166-1 alpha-2; coordinates are
 * optional but must be supplied together when present.
 */
public record HubAddress(
        String addressLine1,
        String addressLine2,
        String locality,
        String administrativeArea,
        String postalCode,
        String countryCode,
        Double latitude,
        Double longitude) {

    public HubAddress {
        addressLine1 = required(addressLine1, "addressLine1");
        locality = required(locality, "locality");
        countryCode = required(countryCode, "countryCode").toUpperCase(java.util.Locale.ROOT);
        if (!countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("countryCode must be ISO alpha-2");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be supplied together");
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
