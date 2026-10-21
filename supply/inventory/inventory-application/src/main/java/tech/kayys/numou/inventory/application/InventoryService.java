package tech.kayys.numou.inventory.application;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;

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

    /** Idempotent receipt; key must remain stable across retries. */
    public InventoryMovement receive(InventoryCommandKey key, ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Received quantity");
        return repository.saveMovementIdempotently(key, new InventoryMovement(MovementId.generate(), itemId,
                warehouseId, MovementType.RECEIPT, quantity, unitCost, currency, ref, Instant.now()));
    }

    /** Inward goods movement: increases stock on-hand. */
    public InventoryMovement receive(ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost,
                                     String currency, String ref) {
        requirePositive(quantity, "Received quantity");
        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.RECEIPT,
                quantity, unitCost, currency, ref, Instant.now());
        repository.saveMovement(movement);
        return movement;
    }

    /** Outward movement: atomically checks on-hand stock and appends the issue. */
    public InventoryMovement issue(ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String ref) {
        requirePositive(quantity, "Issued quantity");
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        InventoryMovement movement = new InventoryMovement(
                MovementId.generate(), itemId, warehouseId, MovementType.ISSUE,
                quantity.negate(), unitCost, currency, ref, Instant.now());
        repository.postIssueIfAvailable(movement, quantity);
        return movement;
    }

    /** Idempotent issue; key must remain stable across retries. */
    public InventoryMovement issue(InventoryCommandKey key, ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Issued quantity");
        StockBalance current = getStockBalance(itemId, warehouseId);
        BigDecimal unitCost = current.onHand().signum() > 0
                ? current.totalValue().divide(current.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return repository.postIssueIfAvailable(key, new InventoryMovement(MovementId.generate(), itemId,
                warehouseId, MovementType.ISSUE, quantity.negate(), unitCost, currency, ref, Instant.now()), quantity);
    }

    /** Atomically posts both sides of an inter-warehouse stock transfer. */
    public void transfer(ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String ref) {
        requirePositive(quantity, "Transfer quantity");
        Objects.requireNonNull(fromWarehouse, "Source warehouse is required");
        Objects.requireNonNull(toWarehouse, "Destination warehouse is required");
        if (fromWarehouse.equals(toWarehouse)) {
            throw new IllegalArgumentException("Transfer source and destination must differ");
        }

        StockBalance source = getStockBalance(itemId, fromWarehouse);
        BigDecimal unitCost = source.onHand().signum() > 0
                ? source.totalValue().divide(source.onHand(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Instant now = Instant.now();
        InventoryMovement outbound = new InventoryMovement(
                MovementId.generate(), itemId, fromWarehouse, MovementType.ISSUE,
                quantity.negate(), unitCost, currency, ref + "-OUT", now);
        InventoryMovement inbound = new InventoryMovement(
                MovementId.generate(), itemId, toWarehouse, MovementType.RECEIPT,
                quantity, unitCost, currency, ref + "-IN", now);
        repository.postTransferIfAvailable(outbound, inbound, quantity);
    }

    /** Idempotent transfer; replay returns the original movement pair. */
    public void transfer(InventoryCommandKey key, ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String ref) {
        Objects.requireNonNull(key, "Command key is required"); requirePositive(quantity, "Transfer quantity");
        Objects.requireNonNull(fromWarehouse, "Source warehouse is required");
        Objects.requireNonNull(toWarehouse, "Destination warehouse is required");
        if (fromWarehouse.equals(toWarehouse)) throw new IllegalArgumentException("Transfer source and destination must differ");
        StockBalance source = getStockBalance(itemId, fromWarehouse);
        BigDecimal unitCost = source.onHand().signum() > 0
                ? source.totalValue().divide(source.onHand(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        Instant now = Instant.now();
        InventoryMovement outbound = new InventoryMovement(MovementId.generate(), itemId, fromWarehouse,
                MovementType.ISSUE, quantity.negate(), unitCost, currency, ref + "-OUT", now);
        InventoryMovement inbound = new InventoryMovement(MovementId.generate(), itemId, toWarehouse,
                MovementType.RECEIPT, quantity, unitCost, currency, ref + "-IN", now);
        repository.postTransferIfAvailable(key, outbound, inbound, quantity);
    }

    /** Real-time stock balance projection derived from the movement log. */
    public StockBalance getStockBalance(ItemId itemId, WarehouseId warehouseId) {
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (InventoryMovement m : repository.findByItemAndWarehouse(itemId, warehouseId)) {
            onHand = onHand.add(m.quantity());
            if (m.type() == MovementType.RECEIPT || m.type() == MovementType.TRANSFER_IN) {
                totalValue = totalValue.add(m.totalCost());
            } else if (m.type() == MovementType.ISSUE || m.type() == MovementType.TRANSFER_OUT) {
                totalValue = totalValue.subtract(m.totalCost());
            }
        }
        if (totalValue.signum() < 0) totalValue = BigDecimal.ZERO;
        return new StockBalance(itemId, warehouseId, onHand, BigDecimal.ZERO, onHand, totalValue);
    }

    public List<InventoryMovement> getMovements(ItemId itemId) {
        return repository.findByItem(itemId);
    }

    private static void requirePositive(BigDecimal quantity, String label) {
        Objects.requireNonNull(quantity, label + " is required");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
