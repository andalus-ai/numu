package tech.kayys.syirkah.catalog.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for a {@link tech.kayys.syirkah.catalog.domain.model.CatalogListing} (config03.md §P4-17 #8).
 */
public record CatalogListingId(UUID value) implements DomainId<UUID> {

    public CatalogListingId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static CatalogListingId generate() {
        return new CatalogListingId(UUID.randomUUID());
    }

    public static CatalogListingId of(UUID value) {
        return new CatalogListingId(value);
    }

    public static CatalogListingId fromString(String uuid) {
        return new CatalogListingId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
