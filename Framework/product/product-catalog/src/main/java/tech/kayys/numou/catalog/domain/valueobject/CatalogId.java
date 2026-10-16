package tech.kayys.numou.catalog.domain.valueobject;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a Catalog (config03.md §P4-17 #8).
 */
public record CatalogId(UUID value) implements DomainId<UUID> {

    public CatalogId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static CatalogId generate() {
        return new CatalogId(UUID.randomUUID());
    }

    public static CatalogId of(UUID value) {
        return new CatalogId(value);
    }

    public static CatalogId fromString(String uuid) {
        return new CatalogId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
