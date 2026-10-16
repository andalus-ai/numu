package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/** Controlled cart totals for promotion evaluation. */
public record CartSnapshot(
        Money subtotal,
        Money shippingAmount,
        Money total
) {

    public CartSnapshot {
        Objects.requireNonNull(subtotal, "subtotal cannot be null");
        Objects.requireNonNull(shippingAmount, "shippingAmount cannot be null");
        Objects.requireNonNull(total, "total cannot be null");
    }
}
