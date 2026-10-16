package tech.kayys.syirkah.construction.domain.masterdata;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionSpecificationId(UUID value) implements DomainId<UUID> {
    public ConstructionSpecificationId { Objects.requireNonNull(value); }
    public static ConstructionSpecificationId generate() { return new ConstructionSpecificationId(UUID.randomUUID()); }
    public static ConstructionSpecificationId of(UUID value) { return new ConstructionSpecificationId(value); }
}
