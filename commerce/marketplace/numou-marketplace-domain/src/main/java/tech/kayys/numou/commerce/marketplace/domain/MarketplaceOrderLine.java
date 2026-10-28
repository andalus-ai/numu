package tech.kayys.numou.commerce.marketplace.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable commercial snapshot for one order line. Identifiers refer to external
 * capability contracts; this value does not copy or own Product/Offering entities.
 */
public record MarketplaceOrderLine(
        String lineId,
        String productId,
        String offeringId,
        String sellerId,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        String currency
) {
    public MarketplaceOrderLine {
        lineId = required(lineId, "lineId");
        productId = required(productId, "productId");
        offeringId = required(offeringId, "offeringId");
        sellerId = required(sellerId, "sellerId");
        description = required(description, "description");
        Objects.requireNonNull(quantity, "quantity is required");
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        if (quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unitPrice.signum() < 0) throw new IllegalArgumentException("unitPrice cannot be negative");
        unit = required(unit, "unit");
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter code");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
