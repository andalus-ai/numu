package tech.kayys.syirkah.company.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Role identifier.
 */
public record RoleId(UUID value) implements DomainId<UUID>, Serializable {

    public RoleId {
        Objects.requireNonNull(value, "RoleId value cannot be null");
    }

    public static RoleId of(UUID value) {
        return new RoleId(value);
    }

    public static RoleId generate() {
        return new RoleId(UUID.randomUUID());
    }

    public static RoleId fromString(String value) {
        return new RoleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "RoleId{" + value + "}";
    }
}
