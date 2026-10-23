package tech.kayys.numou.courier.domain;

import java.util.Objects;

/** Immutable contact and delivery address snapshot captured at booking time. */
public record ShipmentAddress(
        String contactName, String phone, String addressLine1, String addressLine2,
        String locality, String administrativeArea, String postalCode, String countryCode) {
    public ShipmentAddress {
        contactName = required(contactName, "contactName");
        phone = required(phone, "phone");
        addressLine1 = required(addressLine1, "addressLine1");
        addressLine2 = optional(addressLine2);
        locality = required(locality, "locality");
        administrativeArea = optional(administrativeArea);
        postalCode = optional(postalCode);
        countryCode = required(countryCode, "countryCode").toUpperCase(java.util.Locale.ROOT);
        if (!countryCode.matches("[A-Z]{2}")) throw new IllegalArgumentException("countryCode must be ISO alpha-2");
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}