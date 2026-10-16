package tech.kayys.syirkah.construction.domain.site;

public record SiteAddress(
        String addressLine,
        String city,
        String province,
        String postalCode,
        String country
) {
    public SiteAddress {
        if (addressLine == null || addressLine.isBlank()) throw new IllegalArgumentException("Address line cannot be blank");
        if (city == null || city.isBlank()) throw new IllegalArgumentException("City cannot be blank");
        if (country == null || country.isBlank()) throw new IllegalArgumentException("Country cannot be blank");
    }
}
