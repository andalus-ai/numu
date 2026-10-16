package tech.kayys.syirkah.inventory.application;

import tech.kayys.syirkah.inventory.domain.inventory.*;
import tech.kayys.syirkah.inventory.spi.InventoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Application service managing inventory ledger movements, balances, and transfers.
 */
public final class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void registerItem(InventoryItem item) { repository.registerItem(item); }
    public void registerWarehouse(Warehouse wh) { repository.registerWarehouse(wh); }

    /** Inward goods movement: increases stock on-hand. */
    public InventoryMovement receive(ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost,
                                     String currency, String ref) {
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Received quantity must be positive");
        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.RECEIPT,
                quantity, unitCost, currency, ref, Instant.now());
        repository.saveMovement(movement);
        return movement;
    }

    /** Outward movement: checks available stock and issues stock. */
    public InventoryMovement issue(ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String ref) {
        if (quantity.signum() <= 0) throw new IllegalArgumentException("Issued quantity must be positive");
        StockBalance current = getStockBalance(itemId, warehouseId);
        if (current.available().compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient stock: available=" + current.available() + ", requested=" + quantity);
        }

        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.ISSUE,
                quantity.negate(), unitCost, currency, ref, Instant.now());
        repository.saveMovement(movement);
        return movement;
    }

    /** Inter-warehouse stock transfer. */
    public void transfer(ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String ref) {
        issue(itemId, fromWarehouse, quantity, currency, ref + "-OUT");
        StockBalance fromBal = getStockBalance(itemId, fromWarehouse);
        BigDecimal cost = fromBal.onHand().signum() > 0
                ? fromBal.totalValue().divide(fromBal.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        receive(itemId, toWarehouse, quantity, cost, currency, ref + "-IN");
    }

    /** Real-time stock balance projection derived from the movement log. */
    public StockBalance getStockBalance(ItemId itemId, WarehouseId warehouseId) {
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (InventoryMovement m : repository.findByItemAndWarehouse(itemId, warehouseId)) {
            onHand = onHand.add(m.quantity());
            if (m.type() == MovementType.RECEIPT) {
                totalValue = totalValue.add(m.totalCost());
            } else if (m.type() == MovementType.ISSUE) {
                totalValue = totalValue.subtract(m.totalCost());
            }
        }
        if (totalValue.signum() < 0) totalValue = BigDecimal.ZERO;
        return new StockBalance(itemId, warehouseId, onHand, BigDecimal.ZERO, onHand, totalValue);
    }

    public List<InventoryMovement> getMovements(ItemId itemId) {
        return repository.findByItem(itemId);
    }
}
