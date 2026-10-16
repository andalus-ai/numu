package tech.kayys.numou.foundation.domain.ref;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal cross-domain lightweight reference to a User identity.
 * Tenancy and other bounded contexts hold this reference instead of
 * depending on the Identity module directly.
 */
public record UserRef(UUID value) implements DomainId<UUID>, Serializable {

    public UserRef {
        Objects.requireNonNull(value, "UserRef value must not be null");
    }

    public static UserRef of(UUID value) {
        return new UserRef(value);
    }

    public static UserRef of(String value) {
        return new UserRef(UUID.fromString(value));
    }

    public static UserRef generate() {
        return new UserRef(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
