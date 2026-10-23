package tech.kayys.numou.inventory.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.*;
import tech.kayys.numou.inventory.spi.InventoryRepository;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TenantScopedInventoryServiceTest {
    @Test
    void routes_each_operation_through_the_requested_tenant_repository() {
        AtomicReference<String> routedTenant = new AtomicReference<>();
        TenantScopedInventoryService service = new TenantScopedInventoryService(tenant -> {
            routedTenant.set(tenant);
            return repositoryProxy();
        });

        service.registerItem(" tenant-a ", new InventoryItem(ItemId.generate(), "SKU-1", "Item",
                ValuationMethod.WEIGHTED_AVERAGE, TrackingMethod.NONE, "EA"));

        assertEquals("tenant-a", routedTenant.get());
    }

    @Test
    void scopes_idempotency_keys_to_tenant() {
        AtomicReference<InventoryCommandKey> observedKey = new AtomicReference<>();
        TenantScopedInventoryService service = new TenantScopedInventoryService(tenant -> repositoryProxy((method, args) -> {
            if (method.equals("saveMovementIdempotently")) {
                observedKey.set((InventoryCommandKey) args[0]);
                return args[1];
            }
            return defaultValue(method, args);
        }));

        service.receive("tenant-b", new InventoryCommandKey("orders", "request-7"),
                ItemId.generate(), WarehouseId.generate(), new BigDecimal("2"),
                new BigDecimal("3.50"), "IDR", "PO-7");

        assertNotNull(observedKey.get());
        assertEquals("tenant:tenant-b:orders", observedKey.get().scope());
        assertEquals("request-7", observedKey.get().key());
    }

    @Test
    void rejects_blank_tenant_before_calling_provider() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        TenantScopedInventoryService service = new TenantScopedInventoryService(tenant -> {
            called.set(true);
            return repositoryProxy();
        });

        assertThrows(IllegalArgumentException.class, () -> service.getMovements("  ", ItemId.generate()));
        assertFalse(called.get());
    }

    private static InventoryRepository repositoryProxy() {
        return repositoryProxy((method, args) -> defaultValue(method, args));
    }

    private static InventoryRepository repositoryProxy(Invocation invocation) {
        return (InventoryRepository) Proxy.newProxyInstance(InventoryRepository.class.getClassLoader(),
                new Class<?>[]{InventoryRepository.class}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestTenantInventoryRepository";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method.getName(), args == null ? new Object[0] : args);
                });
    }

    private static Object defaultValue(String method, Object[] args) {
        return switch (method) {
            case "saveMovementIdempotently", "postIssueIfAvailable" -> args.length > 0 && args[0] instanceof InventoryCommandKey
                    ? args[1] : null;
            case "findByItem", "findByItemAndWarehouse", "findReservations" -> java.util.List.of();
            default -> null;
        };
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args);
    }
}
