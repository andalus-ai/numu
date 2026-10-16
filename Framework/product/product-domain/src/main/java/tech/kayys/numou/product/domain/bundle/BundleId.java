package tech.kayys.syirkah.product.domain.bundle;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

/** Stable identity of a commercial bundle. */
public record BundleId(UUID value) implements DomainId<UUID> {

    public BundleId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Bundle id cannot be null"
            );
        }
    }

    public static BundleId generate() {
        return new BundleId(UUID.randomUUID());
    }

    public static BundleId of(UUID value) {
        return new BundleId(value);
    }
}
