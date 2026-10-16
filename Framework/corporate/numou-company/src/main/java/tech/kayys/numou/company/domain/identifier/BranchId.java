package tech.kayys.syirkah.company.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Branch identifier.
 */
public record BranchId(UUID value) implements DomainId<UUID>, Serializable {

    public BranchId {
        Objects.requireNonNull(value, "BranchId value cannot be null");
    }

    public static BranchId of(UUID value) {
        return new BranchId(value);
    }

    public static BranchId generate() {
        return new BranchId(UUID.randomUUID());
    }

    public static BranchId fromString(String value) {
        return new BranchId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "BranchId{" + value + "}";
    }
}
