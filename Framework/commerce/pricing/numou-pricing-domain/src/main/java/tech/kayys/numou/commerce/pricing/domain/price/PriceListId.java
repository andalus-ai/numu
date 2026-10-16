package tech.kayys.syirkah.commerce.pricing.domain.price;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a price list (e.g. RETAIL-2026). */
public record PriceListId(UUID value) implements DomainId<UUID> {

    public PriceListId {
        Objects.requireNonNull(value, "priceListId cannot be null");
    }

    public static PriceListId generate() {
        return new PriceListId(UUID.randomUUID());
    }

    public static PriceListId of(UUID value) {
        return new PriceListId(value);
    }
}
