package tech.kayys.syirkah.construction.domain.planning;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionPlanId(UUID value) implements DomainId<UUID> {
    public ConstructionPlanId { Objects.requireNonNull(value); }
    public static ConstructionPlanId generate() { return new ConstructionPlanId(UUID.randomUUID()); }
    public static ConstructionPlanId of(UUID value) { return new ConstructionPlanId(value); }
}
