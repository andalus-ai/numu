package tech.kayys.syirkah.commerce.pricing.domain.price;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Identity of one price-list row. */
public record PriceEntryId(UUID value) implements DomainId<UUID> {

    public PriceEntryId {
        Objects.requireNonNull(value, "priceEntryId cannot be null");
    }

    public static PriceEntryId generate() {
        return new PriceEntryId(UUID.randomUUID());
    }

    public static PriceEntryId of(UUID value) {
        return new PriceEntryId(value);
    }
}
