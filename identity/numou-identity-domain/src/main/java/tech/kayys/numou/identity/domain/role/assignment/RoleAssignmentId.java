package tech.kayys.numou.identity.domain.role.assignment;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record RoleAssignmentId(UUID value) implements DomainId<UUID> {
    public RoleAssignmentId {
        Objects.requireNonNull(value, "RoleAssignmentId value cannot be null");
    }

    public static RoleAssignmentId newId() {
        return new RoleAssignmentId(UUID.randomUUID());
    }

    public static RoleAssignmentId of(UUID value) {
        return new RoleAssignmentId(value);
    }
}
