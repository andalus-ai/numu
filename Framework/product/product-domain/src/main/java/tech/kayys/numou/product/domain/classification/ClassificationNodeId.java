package tech.kayys.syirkah.product.domain.classification;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Stable identity of a classification node (a taxonomy entry). */
public record ClassificationNodeId(UUID value) implements DomainId<UUID> {
    public ClassificationNodeId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Classification node id cannot be null"
            );
        }
    }

    public static ClassificationNodeId generate() {
        return new ClassificationNodeId(UUID.randomUUID());
    }

    public static ClassificationNodeId of(UUID value) {
        return new ClassificationNodeId(value);
    }
}
