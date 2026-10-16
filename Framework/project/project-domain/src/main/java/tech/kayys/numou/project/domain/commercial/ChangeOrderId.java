package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a change order. */
public record ChangeOrderId(UUID value) implements DomainId<UUID> {
    public ChangeOrderId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Change order id cannot be null"
            );
        }
    }

    public static ChangeOrderId generate() {
        return new ChangeOrderId(UUID.randomUUID());
    }

    public static ChangeOrderId of(UUID value) {
        return new ChangeOrderId(value);
    }
}