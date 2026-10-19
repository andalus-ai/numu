package tech.kayys.numou.foundation.domain.ref;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal cross-domain reference to a person entity in the ecosystem.
 */
public record PersonRef(UUID value) implements DomainId<UUID>, Serializable {

    public PersonRef {
        Objects.requireNonNull(value, "PersonRef value must not be null");
    }

    public static PersonRef of(UUID value) {
        return new PersonRef(value);
    }

    public static PersonRef of(String value) {
        return new PersonRef(UUID.fromString(value));
    }

    public static PersonRef generate() {
        return new PersonRef(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
