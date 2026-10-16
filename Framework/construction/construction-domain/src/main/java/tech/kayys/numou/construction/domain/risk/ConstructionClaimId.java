package tech.kayys.syirkah.construction.domain.risk;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionClaimId(UUID value) implements DomainId<UUID> {
    public ConstructionClaimId { Objects.requireNonNull(value); }
    public static ConstructionClaimId generate() { return new ConstructionClaimId(UUID.randomUUID()); }
    public static ConstructionClaimId of(UUID value) { return new ConstructionClaimId(value); }
}
