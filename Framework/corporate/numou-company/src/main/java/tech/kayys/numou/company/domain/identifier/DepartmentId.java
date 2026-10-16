package tech.kayys.syirkah.company.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Department identifier.
 */
public record DepartmentId(UUID value) implements DomainId<UUID>, Serializable {

    public DepartmentId {
        Objects.requireNonNull(value, "DepartmentId value cannot be null");
    }

    public static DepartmentId of(UUID value) {
        return new DepartmentId(value);
    }

    public static DepartmentId generate() {
        return new DepartmentId(UUID.randomUUID());
    }

    public static DepartmentId fromString(String value) {
        return new DepartmentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "DepartmentId{" + value + "}";
    }
}
