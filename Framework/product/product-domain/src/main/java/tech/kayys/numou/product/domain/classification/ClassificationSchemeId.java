package tech.kayys.syirkah.product.domain.classification;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Stable identity of a classification scheme (a taxonomy). */
public record ClassificationSchemeId(UUID value) implements DomainId<UUID> {
    public ClassificationSchemeId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Classification scheme id cannot be null"
            );
        }
    }

    public static ClassificationSchemeId generate() {
        return new ClassificationSchemeId(UUID.randomUUID());
    }

    public static ClassificationSchemeId of(UUID value) {
        return new ClassificationSchemeId(value);
    }
}
