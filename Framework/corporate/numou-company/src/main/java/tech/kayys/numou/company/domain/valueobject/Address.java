package tech.kayys.syirkah.company.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Address value object for company locations.
 */
public final class Address implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final String street;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;
    private final String latitude;
    private final String longitude;

    public Address(String street, String city, String state, String postalCode, String country) {
        this(street, city, state, postalCode, country, null, null);
    }

    public Address(String street, String city, String state, String postalCode, String country,
                   String latitude, String longitude) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        validate();
    }

    @Override
    public void validate() {
        if (street == null || street.trim().isEmpty()) {
            throw new IllegalArgumentException("Street cannot be empty");
        }
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City cannot be empty");
        }
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("Country cannot be empty");
        }
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public String getLatitude() { return latitude; }
    public String getLongitude() { return longitude; }

    public String formattedAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street);
        if (city != null) {
            sb.append(", ").append(city);
        }
        if (state != null) {
            sb.append(", ").append(state);
        }
        if (postalCode != null) {
            sb.append(" ").append(postalCode);
        }
        if (country != null) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(street, address.street) &&
               Objects.equals(city, address.city) &&
               Objects.equals(state, address.state) &&
               Objects.equals(postalCode, address.postalCode) &&
               Objects.equals(country, address.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, city, state, postalCode, country);
    }

    @Override
    public String toString() {
        return formattedAddress();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String latitude;
        private String longitude;

        public Builder street(String street) {
            this.street = street;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder postalCode(String postalCode) {
            this.postalCode = postalCode;
            return this;
        }

        public Builder country(String country) {
            this.country = country;
            return this;
        }

        public Builder latitude(String latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(String longitude) {
            this.longitude = longitude;
            return this;
        }

        public Address build() {
            return new Address(street, city, state, postalCode, country, latitude, longitude);
        }
    }
}
