package tech.kayys.syirkah.construction.domain.cost;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionCostEntryId(UUID value) implements DomainId<UUID> {
    public ConstructionCostEntryId { Objects.requireNonNull(value); }
    public static ConstructionCostEntryId generate() { return new ConstructionCostEntryId(UUID.randomUUID()); }
    public static ConstructionCostEntryId of(UUID value) { return new ConstructionCostEntryId(value); }
}
