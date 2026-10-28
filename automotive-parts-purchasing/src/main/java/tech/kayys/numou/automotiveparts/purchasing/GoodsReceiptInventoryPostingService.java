package tech.kayys.numou.automotiveparts.purchasing;

import tech.kayys.numou.inventory.application.InventoryService;
import tech.kayys.numou.inventory.domain.inventory.InventoryCommandKey;
import tech.kayys.numou.inventory.domain.inventory.InventoryMovement;
import tech.kayys.numou.inventory.domain.inventory.ItemId;
import tech.kayys.numou.inventory.domain.inventory.WarehouseId;
import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Explicit anti-corruption boundary from Purchasing goods receipts to Inventory stock.
 *
 * <p>Purchasing remains authoritative for receipt identity and accepted quantity;
 * Inventory remains authoritative for stock movements and balances. The mapping policy
 * is responsible for resolving the PO line to a tenant-owned stock item, receiving
 * warehouse, unit conversion, and valuation. A stable receipt-derived command key makes
 * retries safe only when the Inventory adapter implements durable idempotency.</p>
 */
public final class GoodsReceiptInventoryPostingService {
    private final InventoryService inventory;
    private final ReceiptInventoryMapping mapping;

    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.mapping = Objects.requireNonNull(mapping, "mapping is required");
    }

    /**
     * Posts the receipt's accepted quantity into Inventory. Mapping must be tenant scoped
     * and deterministic for the lifetime of a receipt; changed mapping/payload for a
     * previously posted receipt is rejected by Inventory's idempotency contract.
     */
    public InventoryMovement post(GoodsReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt is required");
        require(receipt.tenantId(), "tenantId");
        require(receipt.receiptId(), "receiptId");
        require(receipt.poId(), "poId");
        require(receipt.poLineId(), "poLineId");

        ReceiptInventoryTarget target = Objects.requireNonNull(
                mapping.resolve(receipt.tenantId(), receipt.poId(), receipt.poLineId()),
                "No inventory mapping exists for this purchase-order line");
        BigDecimal inventoryQuantity = receipt.quantityReceived().multiply(target.inventoryUnitsPerReceiptUnit());
        if (inventoryQuantity.signum() <= 0) {
            throw new IllegalArgumentException("Converted inventory quantity must be positive");
        }

        InventoryCommandKey key = new InventoryCommandKey(
                "purchasing.goods-receipt:" + receipt.tenantId(), receipt.receiptId());
        return inventory.receive(key, target.itemId(), target.warehouseId(), inventoryQuantity,
                target.unitCost(), target.currency(), "goods-receipt:" + receipt.receiptId());
    }

    @FunctionalInterface
    public interface ReceiptInventoryMapping {
        ReceiptInventoryTarget resolve(String tenantId, String purchaseOrderId, String purchaseOrderLineId);
    }

    /**
     * Versioned business mapping result. Conversion factor means inventory base units per
     * receipt unit (e.g. 12 EA per BOX); unit cost is expressed per inventory base unit.
     */
    public record ReceiptInventoryTarget(
            ItemId itemId,
            WarehouseId warehouseId,
            BigDecimal inventoryUnitsPerReceiptUnit,
            BigDecimal unitCost,
            String currency) {
        public ReceiptInventoryTarget {
            Objects.requireNonNull(itemId, "itemId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            Objects.requireNonNull(inventoryUnitsPerReceiptUnit, "unit conversion is required");
            Objects.requireNonNull(unitCost, "unitCost is required");
            require(currency, "currency");
            if (inventoryUnitsPerReceiptUnit.signum() <= 0) {
                throw new IllegalArgumentException("Unit conversion must be positive");
            }
            if (unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost must not be negative");
            currency = currency.trim();
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
