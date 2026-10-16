package tech.kayys.syirkah.ecosystem.domain.contact;

import java.io.Serializable;
import java.util.Locale;

/**
 * Structured postal fields with ISO 3166-1 alpha-2 country validation (config03.md §P4-13 #3).
 */
public record PostalAddress(
        String addressLine1,
        String addressLine2,
        String locality,
        String administrativeArea,
        String postalCode,
        String countryCode
) implements Serializable {

    public PostalAddress {
        addressLine1 = normalizeOptional(addressLine1);
        addressLine2 = normalizeOptional(addressLine2);
        locality = normalizeOptional(locality);
        administrativeArea = normalizeOptional(administrativeArea);
        postalCode = normalizeOptional(postalCode);

        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("countryCode is required");
        }

        countryCode = countryCode.trim().toUpperCase(Locale.ROOT);

        if (!countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("countryCode must be an ISO 3166-1 alpha-2 code");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
