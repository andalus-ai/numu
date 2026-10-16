package tech.kayys.syirkah.construction.domain.contract;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionContractId(UUID value) implements DomainId<UUID> {
    public ConstructionContractId { Objects.requireNonNull(value, "Contract id cannot be null"); }
    public static ConstructionContractId generate() { return new ConstructionContractId(UUID.randomUUID()); }
    public static ConstructionContractId of(UUID value) { return new ConstructionContractId(value); }
}
