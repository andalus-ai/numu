package tech.kayys.numou.automotiveparts.inventory;

import tech.kayys.numou.inventory.application.TenantScopedInventoryService;
import tech.kayys.numou.inventory.domain.inventory.InventoryCommandKey;
import tech.kayys.numou.inventory.domain.inventory.InventoryMovement;
import tech.kayys.numou.inventory.domain.inventory.ItemId;
import tech.kayys.numou.inventory.domain.inventory.WarehouseId;
import tech.kayys.numou.inventory.domain.reservation.InventoryReservation;
import tech.kayys.numou.inventory.domain.reservation.ReservationId;
import tech.kayys.numou.inventory.domain.reservation.ReservationStatus;
import tech.kayys.numou.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-capability coordinator for workshop parts. Inventory remains the owner of stock,
 * reservations and movements; this class only supplies work-order context and stable command keys.
 * Each command key must be persisted by the chosen inventory adapter for durable retry safety.
 */
public final class WorkshopPartsInventoryService {
    private final TenantScopedInventoryService inventory;
    private final ProductInventoryItemResolver itemResolver;

    public WorkshopPartsInventoryService(TenantScopedInventoryService inventory,
                                         ProductInventoryItemResolver itemResolver) {
        this.inventory = Objects.requireNonNull(inventory, "inventory is required");
        this.itemResolver = Objects.requireNonNull(itemResolver, "itemResolver is required");
    }

    public InventoryReservation reserve(ReservePartCommand command) {
        Objects.requireNonNull(command, "command is required");
        ItemId item = resolve(command.tenantId(), command.productId());
        return inventory.reserve(command.tenantId(),
                key("reserve", command.workOrderId(), command.workOrderLineId(), command.idempotencyKey()),
                item, command.warehouseId(), command.quantity(), command.uom(),
                reference(command.workOrderId(), command.workOrderLineId()), command.expiresAt());
    }

    /** Issues the complete reserved line quantity; partial issue requires a separate split reservation. */
    public InventoryMovement issue(IssuePartCommand command) {
        Objects.requireNonNull(command, "command is required");
        ItemId item = resolve(command.tenantId(), command.productId());
        return inventory.issueReserved(command.tenantId(),
                key("issue", command.workOrderId(), command.workOrderLineId(), command.idempotencyKey()),
                ReservationId.of(command.reservationId()), item, command.warehouseId(),
                command.quantity(), command.currency(), reference(command.workOrderId(), command.workOrderLineId()));
    }

    /** Returns unused parts as an idempotent receipt with caller-supplied traceable valuation. */
    public InventoryMovement returnUnused(ReturnPartCommand command) {
        Objects.requireNonNull(command, "command is required");
        ItemId item = resolve(command.tenantId(), command.productId());
        String reference = reference(command.workOrderId(), command.workOrderLineId()) + ":return:" + command.idempotencyKey();
        return inventory.receive(command.tenantId(),
                key("return", command.workOrderId(), command.workOrderLineId(), command.idempotencyKey()),
                item, command.warehouseId(), command.quantity(), command.unitCost(),
                command.currency(), reference);
    }

    /**
     * Releases active reservations for a cancelled work order. Committed reservations are retained:
     * their issue movements are historical facts and cancellation must not reverse them implicitly.
     * Repeating this command is safe for reservations already released or expired.
     */
    public List<InventoryReservation> releaseForCancellation(String tenantId, List<String> reservationIds) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (reservationIds == null) throw new IllegalArgumentException("reservationIds are required");
        List<InventoryReservation> results = new ArrayList<>();
        for (String rawId : reservationIds) {
            ReservationId id = ReservationId.of(required(rawId, "reservationId"));
            InventoryReservation current = inventory.findReservation(tenantId, id);
            if (current.status() == ReservationStatus.ACTIVE) {
                current = inventory.releaseReservationIfActive(tenantId, id);
            }
            results.add(current);
        }
        return List.copyOf(results);
    }

    private ItemId resolve(String tenantId, ProductId productId) {
        Objects.requireNonNull(productId, "productId is required");
        return Objects.requireNonNull(itemResolver.resolve(tenantId, productId),
                "Product-to-inventory mapping did not resolve an item");
    }

    private static InventoryCommandKey key(String action, UUID workOrderId, UUID lineId, String idempotencyKey) {
        return new InventoryCommandKey("workshop-parts:" + action + ":" + requiredId(workOrderId, "workOrderId")
                + ":" + requiredId(lineId, "workOrderLineId"), required(idempotencyKey, "idempotencyKey"));
    }

    private static String reference(UUID workOrderId, UUID lineId) {
        return "work-order:" + requiredId(workOrderId, "workOrderId") + ":line:" + requiredId(lineId, "workOrderLineId");
    }

    private static String requiredId(UUID id, String name) {
        return Objects.requireNonNull(id, name + " is required").toString();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    public record ReservePartCommand(String tenantId, UUID workOrderId, UUID workOrderLineId,
                                     ProductId productId, WarehouseId warehouseId, BigDecimal quantity,
                                     String uom, Instant expiresAt, String idempotencyKey) {
        public ReservePartCommand {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(workOrderId, "workOrderId is required");
            Objects.requireNonNull(workOrderLineId, "workOrderLineId is required");
            Objects.requireNonNull(productId, "productId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (uom == null || uom.isBlank()) throw new IllegalArgumentException("uom is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        }
    }

    public record IssuePartCommand(String tenantId, UUID workOrderId, UUID workOrderLineId, ProductId productId,
                                   String reservationId, WarehouseId warehouseId, BigDecimal quantity,
                                   String currency, String idempotencyKey) {
        public IssuePartCommand {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(workOrderId, "workOrderId is required");
            Objects.requireNonNull(workOrderLineId, "workOrderLineId is required");
            Objects.requireNonNull(productId, "productId is required");
            if (reservationId == null || reservationId.isBlank()) throw new IllegalArgumentException("reservationId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        }
    }

    public record ReturnPartCommand(String tenantId, UUID workOrderId, UUID workOrderLineId, ProductId productId,
                                    WarehouseId warehouseId, BigDecimal quantity, BigDecimal unitCost,
                                    String currency, String idempotencyKey) {
        public ReturnPartCommand {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            Objects.requireNonNull(workOrderId, "workOrderId is required");
            Objects.requireNonNull(workOrderLineId, "workOrderLineId is required");
            Objects.requireNonNull(productId, "productId is required");
            Objects.requireNonNull(warehouseId, "warehouseId is required");
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
            if (unitCost == null || unitCost.signum() < 0) throw new IllegalArgumentException("unitCost cannot be negative");
            if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency is required");
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        }
    }
}