package tech.kayys.numou.automotiveparts.purchasing;

import tech.kayys.numou.inventory.application.InventoryService;
import tech.kayys.numou.inventory.domain.inventory.InventoryCommandKey;
import tech.kayys.numou.inventory.domain.inventory.InventoryMovement;
import tech.kayys.numou.inventory.domain.inventory.ItemId;
import tech.kayys.numou.inventory.domain.inventory.WarehouseId;
import tech.kayys.numou.purchasing.domain.procurement.GoodsReceipt;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicit anti-corruption boundary from accepted Purchasing receipts to Inventory stock. */
public final class GoodsReceiptInventoryPostingService {
    private final InventoryService inventory;
    private final ReceiptInventoryMapping mapping;
    private final ReceiptPostingLedger postingLedger;
    private final ReceiptPostingRetryPolicy retryPolicy;

    /** Compatibility constructor for tests and single-process use. */
    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping) {
        this(inventory, mapping, null, ReceiptPostingRetryPolicy.defaults());
    }

    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping,
                                               ReceiptPostingLedger postingLedger) {
        this(inventory, mapping, postingLedger, ReceiptPostingRetryPolicy.defaults());
    }

    public GoodsReceiptInventoryPostingService(InventoryService inventory, ReceiptInventoryMapping mapping,
                                               ReceiptPostingLedger postingLedger,
                                               ReceiptPostingRetryPolicy retryPolicy) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.mapping = Objects.requireNonNull(mapping, "mapping is required");
        this.postingLedger = postingLedger;
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy is required");
    }

    public InventoryMovement post(GoodsReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt is required");
        require(receipt.tenantId(), "tenantId"); require(receipt.receiptId(), "receiptId");
        require(receipt.poId(), "poId"); require(receipt.poLineId(), "poLineId");
        ReceiptInventoryTarget target = Objects.requireNonNull(
                mapping.resolve(receipt.tenantId(), receipt.poId(), receipt.poLineId()),
                "No inventory mapping exists for this purchase-order line");
        BigDecimal inventoryQuantity = receipt.quantityReceived().multiply(target.inventoryUnitsPerReceiptUnit());
        if (inventoryQuantity.signum() <= 0) throw new IllegalArgumentException("Converted inventory quantity must be positive");

        InventoryCommandKey key = new InventoryCommandKey(
                "purchasing.goods-receipt:" + receipt.tenantId(), receipt.receiptId());
        String reference = "goods-receipt:" + receipt.receiptId();
        if (postingLedger == null) {
            return inventory.receive(key, target.itemId(), target.warehouseId(), inventoryQuantity,
                    target.unitCost(), target.currency(), reference);
        }

        String fingerprint = fingerprint(receipt, target, inventoryQuantity);
        ReceiptPostingLedger.PostingRecord record = postingLedger.begin(
                receipt.tenantId(), receipt.receiptId(), fingerprint, retryPolicy.maxAttempts());
        if (record.status() == ReceiptPostingLedger.Status.DEAD_LETTER) {
            throw new IllegalStateException("Goods receipt posting is dead-lettered and requires authorized operator requeue");
        }

        // Replay POSTED and PENDING through the stable Inventory receipt. The durable command
        // receipt is authoritative after a crash between Inventory commit and ledger update.
        try {
            InventoryMovement movement = inventory.receive(key, target.itemId(), target.warehouseId(),
                    inventoryQuantity, target.unitCost(), target.currency(), reference);
            postingLedger.markPosted(receipt.tenantId(), receipt.receiptId(), fingerprint, movement.id().toString());
            return movement;
        } catch (RuntimeException failure) {
            try {
                postingLedger.markRetryable(receipt.tenantId(), receipt.receiptId(), fingerprint,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage(), retryPolicy.maxAttempts());
            } catch (RuntimeException ledgerFailure) {
                failure.addSuppressed(ledgerFailure);
            }
            throw failure;
        }
    }

    private static String fingerprint(GoodsReceipt receipt, ReceiptInventoryTarget target, BigDecimal quantity) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            String canonical = String.join("\n", receipt.tenantId(), receipt.receiptId(), receipt.poId(),
                    receipt.poLineId(), receipt.quantityReceived().stripTrailingZeros().toPlainString(),
                    target.itemId().toString(), target.warehouseId().toString(),
                    target.inventoryUnitsPerReceiptUnit().stripTrailingZeros().toPlainString(),
                    quantity.stripTrailingZeros().toPlainString(), target.unitCost().stripTrailingZeros().toPlainString(),
                    target.currency());
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

    /** Conversion factor is inventory base units per receipt unit; unit cost is per base unit. */
    public record ReceiptInventoryTarget(ItemId itemId, WarehouseId warehouseId,
            BigDecimal inventoryUnitsPerReceiptUnit, BigDecimal unitCost, String currency) {
        public ReceiptInventoryTarget {
            Objects.requireNonNull(itemId, "itemId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            Objects.requireNonNull(inventoryUnitsPerReceiptUnit, "unit conversion is required");
            Objects.requireNonNull(unitCost, "unitCost is required");
            require(currency, "currency");
            if (inventoryUnitsPerReceiptUnit.signum() <= 0) throw new IllegalArgumentException("Unit conversion must be positive");
            if (unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost must not be negative");
            currency = currency.trim();
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
