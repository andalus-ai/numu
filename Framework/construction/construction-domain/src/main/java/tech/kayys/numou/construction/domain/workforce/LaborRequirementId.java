package tech.kayys.syirkah.construction.domain.workforce;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record LaborRequirementId(UUID value) implements DomainId<UUID> {
    public LaborRequirementId { Objects.requireNonNull(value, "Labor requirement id cannot be null"); }
    public static LaborRequirementId generate() { return new LaborRequirementId(UUID.randomUUID()); }
    public static LaborRequirementId of(UUID value) { return new LaborRequirementId(value); }
}
