package tech.kayys.syirkah.construction.domain.boq;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record BoqItemId(UUID value) implements DomainId<UUID> {
    public BoqItemId { Objects.requireNonNull(value, "BOQ item id cannot be null"); }
    public static BoqItemId generate() { return new BoqItemId(UUID.randomUUID()); }
    public static BoqItemId of(UUID value) { return new BoqItemId(value); }
}
