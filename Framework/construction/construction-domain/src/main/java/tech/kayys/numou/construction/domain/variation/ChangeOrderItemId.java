package tech.kayys.syirkah.construction.domain.variation;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ChangeOrderItemId(UUID value) implements DomainId<UUID> {
    public ChangeOrderItemId { Objects.requireNonNull(value, "Change order item id cannot be null"); }
    public static ChangeOrderItemId generate() { return new ChangeOrderItemId(UUID.randomUUID()); }
    public static ChangeOrderItemId of(UUID value) { return new ChangeOrderItemId(value); }
}
