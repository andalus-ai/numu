package tech.kayys.syirkah.construction.domain.boq;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record BoqId(UUID value) implements DomainId<UUID> {
    public BoqId { Objects.requireNonNull(value, "BOQ id cannot be null"); }
    public static BoqId generate() { return new BoqId(UUID.randomUUID()); }
    public static BoqId of(UUID value) { return new BoqId(value); }
}
