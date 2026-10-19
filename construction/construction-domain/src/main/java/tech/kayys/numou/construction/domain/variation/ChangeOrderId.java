package tech.kayys.numou.construction.domain.variation;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ChangeOrderId(UUID value) implements DomainId<UUID> {
    public ChangeOrderId { Objects.requireNonNull(value, "Change order id cannot be null"); }
    public static ChangeOrderId generate() { return new ChangeOrderId(UUID.randomUUID()); }
    public static ChangeOrderId of(UUID value) { return new ChangeOrderId(value); }
}
