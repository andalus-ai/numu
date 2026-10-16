package tech.kayys.syirkah.ecosystem.domain.contact;

import java.io.Serializable;
import java.util.Objects;

/**
 * Canonical address location and lifecycle model (config03.md §P4-13).
 * Represents a physical or postal location, independent of party ownership.
 */
public record Address(
        AddressId id,
        AddressKind kind,
        PostalAddress postalAddress,
        boolean active
) implements Serializable {

    public Address {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(postalAddress, "postalAddress cannot be null");
    }

    public static Address create(AddressId id, AddressKind kind, PostalAddress postalAddress) {
        return new Address(id, kind, postalAddress, true);
    }

    public Address withActive(boolean newActive) {
        return new Address(id, kind, postalAddress, newActive);
    }

    public Address withPostalAddress(PostalAddress newPostalAddress) {
        return new Address(id, kind, newPostalAddress, active);
    }
}
