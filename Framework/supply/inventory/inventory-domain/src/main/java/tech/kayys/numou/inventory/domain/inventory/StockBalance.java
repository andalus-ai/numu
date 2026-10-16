package tech.kayys.syirkah.inventory.domain.inventory;

import java.math.BigDecimal;
import java.util.Objects;

/** Read-model projection representing real-time stock levels. */
public record StockBalance(
        ItemId itemId,
        WarehouseId warehouseId,
        BigDecimal onHand,
        BigDecimal reserved,
        BigDecimal available,
        BigDecimal totalValue
) {
    public StockBalance {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(warehouseId);
        Objects.requireNonNull(onHand);
        Objects.requireNonNull(reserved);
        Objects.requireNonNull(available);
        Objects.requireNonNull(totalValue);
    }

    public static StockBalance empty(ItemId itemId, WarehouseId warehouseId) {
        return new StockBalance(itemId, warehouseId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
