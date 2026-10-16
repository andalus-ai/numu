package tech.kayys.syirkah.construction.domain.controls;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ProjectControlBaselineId(UUID value) implements DomainId<UUID> {
    public ProjectControlBaselineId { Objects.requireNonNull(value); }
    public static ProjectControlBaselineId generate() { return new ProjectControlBaselineId(UUID.randomUUID()); }
    public static ProjectControlBaselineId of(UUID value) { return new ProjectControlBaselineId(value); }
}
