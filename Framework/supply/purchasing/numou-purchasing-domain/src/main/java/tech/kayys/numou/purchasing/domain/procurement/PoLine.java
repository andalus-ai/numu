
package tech.kayys.syirkah.purchasing.domain.procurement;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

public record PoLine(
        String lineId,
        String itemCode,
        String itemDescription,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        Money unitPrice
) {
    public PoLine {
        Objects.requireNonNull(lineId, "lineId cannot be null");
        Objects.requireNonNull(itemCode, "itemCode cannot be null");
        Objects.requireNonNull(itemDescription, "itemDescription cannot be null");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity cannot be null");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity cannot be null");
        Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
    }

    public static PoLine of(String lineId, String itemCode, String description, BigDecimal qty, Money unitPrice) {
        return new PoLine(lineId, itemCode, description, qty, BigDecimal.ZERO, unitPrice);
    }

    public Money lineTotal() {
        return unitPrice.multiply(orderedQuantity);
    }

    public PoLine withReceived(BigDecimal addQty) {
        return new PoLine(lineId, itemCode, itemDescription, orderedQuantity, receivedQuantity.add(addQty), unitPrice);
    }

    public boolean isFullyReceived() {
        return receivedQuantity.compareTo(orderedQuantity) >= 0;
    }
}
