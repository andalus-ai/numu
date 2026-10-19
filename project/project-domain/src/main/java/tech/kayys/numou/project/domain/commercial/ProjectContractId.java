package tech.kayys.numou.project.domain.commercial;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a project contract. */
public record ProjectContractId(UUID value) implements DomainId<UUID> {
    public ProjectContractId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Contract id cannot be null"
            );
        }
    }

    public static ProjectContractId generate() {
        return new ProjectContractId(UUID.randomUUID());
    }

    public static ProjectContractId of(UUID value) {
        return new ProjectContractId(value);
    }
}