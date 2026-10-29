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
    private final ReceiptPostingLedger postingLedger;

    /** Compatibility constructor for tests and single-process use. Production deployments should inject a durable ledger. */
    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping) {
        this(inventory, mapping, null);
    }

    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping,
                                               ReceiptPostingLedger postingLedger) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.mapping = Objects.requireNonNull(mapping, "mapping is required");
        this.postingLedger = postingLedger;
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
        if (postingLedger == null) {
            return inventory.receive(key, target.itemId(), target.warehouseId(), inventoryQuantity,
                    target.unitCost(), target.currency(), "goods-receipt:" + receipt.receiptId());
        }

        String fingerprint = fingerprint(receipt, target, inventoryQuantity);
        postingLedger.begin(receipt.tenantId(), receipt.receiptId(), fingerprint);
        // Even POSTED records are replayed through Inventory's durable command receipt so
        // the canonical movement can be returned without storing a second copy here.
        try {
            InventoryMovement movement = inventory.receive(key, target.itemId(), target.warehouseId(),
                    inventoryQuantity, target.unitCost(), target.currency(),
                    "goods-receipt:" + receipt.receiptId());
            postingLedger.markPosted(receipt.tenantId(), receipt.receiptId(), fingerprint, movement.id().toString());
            return movement;
        } catch (RuntimeException failure) {
            postingLedger.markRetryable(receipt.tenantId(), receipt.receiptId(), fingerprint,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
            throw failure;
        }
    }

    private static String fingerprint(GoodsReceipt receipt, ReceiptInventoryTarget target,
                                      BigDecimal inventoryQuantity) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String canonical = String.join("\\n", receipt.tenantId(), receipt.receiptId(),
                    receipt.poId(), receipt.poLineId(), receipt.quantityReceived().stripTrailingZeros().toPlainString(),
                    target.itemId().toString(), target.warehouseId().toString(),
                    target.inventoryUnitsPerReceiptUnit().stripTrailingZeros().toPlainString(),
                    inventoryQuantity.stripTrailingZeros().toPlainString(),
                    target.unitCost().stripTrailingZeros().toPlainString(), target.currency());
            return java.util.HexFormat.of().formatHex(digest.digest(
                    canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
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
