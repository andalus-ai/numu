package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Customer identifier in Accounting context.
 */
public record CustomerId(UUID value) implements DomainId<UUID> {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId value cannot be null");
    }

    public UUID getValue() {
        return value;
    }

    public static CustomerId generate() {
        return new CustomerId(UUID.randomUUID());
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId of(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    public static CustomerId fromString(String value) {
        return new CustomerId(UUID.fromString(value));
    }
}
