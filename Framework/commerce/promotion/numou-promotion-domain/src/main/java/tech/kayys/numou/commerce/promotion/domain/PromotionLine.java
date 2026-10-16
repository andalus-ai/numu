package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * One priced line the promotion engine may consider.
 *
 * {@code lineRef} is an opaque tag the conditions match against —
 * typically the offering id as string, a product code, or a category
 * label like "COFFEE". The promotion capability deliberately does not
 * import offering/product aggregates; the cart builder (caller) decides
 * what tag a line carries. Unit price is expected to be the resolved
 * price from the pricing capability, not a raw product field.
 */
public record PromotionLine(
        String lineRef,
        long quantity,
        Money unitPrice
) {

    public PromotionLine {
        Objects.requireNonNull(lineRef, "lineRef cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        lineRef = lineRef.trim();
        if (lineRef.isBlank()) {
            throw new IllegalArgumentException("lineRef cannot be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    /** lineRef × unitPrice. */
    public Money lineTotal() {
        return unitPrice.multiply(java.math.BigDecimal.valueOf(quantity));
    }
}
