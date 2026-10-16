
package tech.kayys.syirkah.purchasing.domain.procurement;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record RequisitionLine(
        String lineId,
        String itemDescription,
        BigDecimal quantity,
        Money estimatedUnitPrice
) {
    public RequisitionLine {
        Objects.requireNonNull(lineId, "lineId cannot be null");
        Objects.requireNonNull(itemDescription, "itemDescription cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(estimatedUnitPrice, "estimatedUnitPrice cannot be null");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public Money estimatedTotal() {
        return estimatedUnitPrice.multiply(quantity);
    }
}
