package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a project claim. */
public record ProjectClaimId(UUID value) implements DomainId<UUID> {
    public ProjectClaimId {
        Objects.requireNonNull(value, "Claim id cannot be null");
    }

    public static ProjectClaimId generate() {
        return new ProjectClaimId(UUID.randomUUID());
    }

    public static ProjectClaimId of(UUID value) {
        return new ProjectClaimId(value);
    }
}