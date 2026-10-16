package tech.kayys.syirkah.construction.domain.procurement;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record MaterialRequirementId(UUID value) implements DomainId<UUID> {
    public MaterialRequirementId { Objects.requireNonNull(value, "Material requirement id cannot be null"); }
    public static MaterialRequirementId generate() { return new MaterialRequirementId(UUID.randomUUID()); }
    public static MaterialRequirementId of(UUID value) { return new MaterialRequirementId(value); }
}
