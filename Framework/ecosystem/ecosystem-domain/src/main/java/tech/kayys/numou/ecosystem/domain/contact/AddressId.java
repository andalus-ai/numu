package tech.kayys.syirkah.ecosystem.domain.contact;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for an {@link Address} (config03.md §P4-13).
 */
public record AddressId(UUID value) implements DomainId<UUID> {

    public AddressId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static AddressId generate() {
        return new AddressId(UUID.randomUUID());
    }

    public static AddressId of(UUID value) {
        return new AddressId(value);
    }

    public static AddressId fromString(String uuid) {
        return new AddressId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
