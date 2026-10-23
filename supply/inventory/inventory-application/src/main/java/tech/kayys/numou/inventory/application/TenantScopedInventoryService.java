package tech.kayys.numou.inventory.application;

import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.domain.reservation.*;
import tech.kayys.numou.inventory.spi.TenantInventoryRepositoryProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Tenant-explicit entry point for inventory operations.
 *
 * <p>Unlike {@link InventoryService}, every public operation requires a tenant
 * identifier. The provider must return a repository whose entire data-access
 * surface is scoped to that tenant. This facade is an application boundary, not
 * a replacement for authorization or database-enforced row-level isolation.</p>
 *
 * <p>Tenant is included in idempotency scope so the same caller key used by two
 * tenants cannot collide when a provider stores command receipts in shared tables.
 * Persistent adapters must still enforce uniqueness transactionally.</p>
 */
public final class TenantScopedInventoryService {
    private static final int MAX_TENANT_ID_LENGTH = 80;

    private final TenantInventoryRepositoryProvider repositories;

    public TenantScopedInventoryService(TenantInventoryRepositoryProvider repositories) {
        this.repositories = Objects.requireNonNull(repositories, "Tenant repository provider is required");
    }

    public void registerItem(String tenantId, InventoryItem item) {
        service(tenantId).registerItem(Objects.requireNonNull(item, "Inventory item is required"));
    }

    public void registerWarehouse(String tenantId, Warehouse warehouse) {
        service(tenantId).registerWarehouse(Objects.requireNonNull(warehouse, "Warehouse is required"));
    }

    public InventoryMovement receive(String tenantId, ItemId itemId, WarehouseId warehouseId,
                                     BigDecimal quantity, BigDecimal unitCost, String currency, String reference) {
        return service(tenantId).receive(itemId, warehouseId, quantity, unitCost, currency, reference);
    }

    public InventoryMovement receive(String tenantId, InventoryCommandKey key, ItemId itemId,
                                     WarehouseId warehouseId, BigDecimal quantity, BigDecimal unitCost,
                                     String currency, String reference) {
        return service(tenantId).receive(scopedKey(tenantId, key), itemId, warehouseId,
                quantity, unitCost, currency, reference);
    }

    public InventoryMovement issue(String tenantId, ItemId itemId, WarehouseId warehouseId,
                                   BigDecimal quantity, String currency, String reference) {
        return service(tenantId).issue(itemId, warehouseId, quantity, currency, reference);
    }

    public InventoryMovement issue(String tenantId, InventoryCommandKey key, ItemId itemId,
                                   WarehouseId warehouseId, BigDecimal quantity,
                                   String currency, String reference) {
        return service(tenantId).issue(scopedKey(tenantId, key), itemId, warehouseId,
                quantity, currency, reference);
    }

    public void transfer(String tenantId, ItemId itemId, WarehouseId fromWarehouse, WarehouseId toWarehouse,
                         BigDecimal quantity, String currency, String reference) {
        service(tenantId).transfer(itemId, fromWarehouse, toWarehouse, quantity, currency, reference);
    }

    public void transfer(String tenantId, InventoryCommandKey key, ItemId itemId, WarehouseId fromWarehouse,
                         WarehouseId toWarehouse, BigDecimal quantity, String currency, String reference) {
        service(tenantId).transfer(scopedKey(tenantId, key), itemId, fromWarehouse, toWarehouse,
                quantity, currency, reference);
    }

    public InventoryReservation reserve(String tenantId, ItemId itemId, WarehouseId warehouseId,
                                        BigDecimal quantity, String uom, String sourceReference, Instant expiresAt) {
        String normalizedTenant = requireTenant(tenantId);
        return service(normalizedTenant).reserve(normalizedTenant, itemId, warehouseId,
                quantity, uom, sourceReference, expiresAt);
    }

    public InventoryReservation releaseReservation(String tenantId, ReservationId reservationId) {
        String normalizedTenant = requireTenant(tenantId);
        return service(normalizedTenant).releaseReservation(normalizedTenant, reservationId);
    }

    public InventoryReservation expireReservation(String tenantId, ReservationId reservationId) {
        String normalizedTenant = requireTenant(tenantId);
        return service(normalizedTenant).expireReservation(normalizedTenant, reservationId);
    }

    public InventoryMovement issueReserved(String tenantId, ReservationId reservationId, ItemId itemId,
                                           WarehouseId warehouseId, BigDecimal quantity,
                                           String currency, String reference) {
        String normalizedTenant = requireTenant(tenantId);
        return service(normalizedTenant).issueReserved(normalizedTenant, reservationId, itemId,
                warehouseId, quantity, currency, reference);
    }

    /** Idempotent reserved issue for retry-safe warehouse picking. */
    public InventoryMovement issueReserved(String tenantId, InventoryCommandKey key, ReservationId reservationId,
                                           ItemId itemId, WarehouseId warehouseId, BigDecimal quantity,
                                           String currency, String reference) {
        String normalizedTenant = requireTenant(tenantId);
        return service(normalizedTenant).issueReserved(normalizedTenant, scopedKey(normalizedTenant, key),
                reservationId, itemId, warehouseId, quantity, currency, reference);
    }

    public StockBalance getStockBalance(String tenantId, ItemId itemId, WarehouseId warehouseId) {
        return service(tenantId).getStockBalance(itemId, warehouseId);
    }

    public List<InventoryMovement> getMovements(String tenantId, ItemId itemId) {
        return service(tenantId).getMovements(itemId);
    }

    private InventoryService service(String tenantId) {
        String normalizedTenant = requireTenant(tenantId);
        InventoryRepository repository = Objects.requireNonNull(repositories.forTenant(normalizedTenant),
                "Tenant repository provider returned no repository");
        return new InventoryService(repository);
    }

    private static InventoryCommandKey scopedKey(String tenantId, InventoryCommandKey key) {
        String normalizedTenant = requireTenant(tenantId);
        Objects.requireNonNull(key, "Idempotency key is required");
        String scope = "tenant:" + normalizedTenant + ":" + key.scope();
        if (scope.length() > 200) {
            throw new IllegalArgumentException("Tenant-scoped idempotency scope exceeds 200 characters");
        }
        return new InventoryCommandKey(scope, key.key());
    }

    private static String requireTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "Tenant id is required");
        String normalized = tenantId.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Tenant id must not be blank");
        if (normalized.length() > MAX_TENANT_ID_LENGTH) {
            throw new IllegalArgumentException("Tenant id must not exceed " + MAX_TENANT_ID_LENGTH + " characters");
        }
        if (normalized.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Tenant id must not contain ':'");
        }
        return normalized;
    }
}
