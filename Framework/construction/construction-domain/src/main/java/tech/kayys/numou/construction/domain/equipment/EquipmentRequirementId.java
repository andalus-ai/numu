package tech.kayys.syirkah.construction.domain.equipment;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record EquipmentRequirementId(UUID value) implements DomainId<UUID> {
    public EquipmentRequirementId { Objects.requireNonNull(value, "Equipment requirement id cannot be null"); }
    public static EquipmentRequirementId generate() { return new EquipmentRequirementId(UUID.randomUUID()); }
    public static EquipmentRequirementId of(UUID value) { return new EquipmentRequirementId(value); }
}
