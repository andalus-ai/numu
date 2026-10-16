package tech.kayys.syirkah.purchasing.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for vendors.
 */
public record VendorId(String value) implements DomainId<String>, Serializable {

    public VendorId {
        Objects.requireNonNull(value, "VendorId value cannot be null");
    }

    public static VendorId of(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Vendor UUID cannot be null");
        }
        return new VendorId(uuid.toString());
    }

    public static VendorId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor ID cannot be empty");
        }
        return new VendorId(value);
    }

    public static VendorId generate() {
        return new VendorId(UUID.randomUUID().toString());
    }

    public UUID toUUID() {
        return UUID.fromString(getValue());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VendorId that = (VendorId) o;
        return Objects.equals(getValue(), that.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue());
    }

    @Override
    public String toString() {
        return "VendorId{" + getValue() + "}";
    }
}
