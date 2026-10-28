package tech.kayys.numou.automotiveparts.inventory;

import tech.kayys.numou.inventory.domain.inventory.ItemId;
import tech.kayys.numou.product.domain.product.ProductId;

/**
 * Explicit composition boundary between canonical product identity and inventory item identity.
 * A deployment may use a deterministic mapping or a tenant-scoped catalog mapping; callers must
 * not assume ProductId and ItemId are interchangeable.
 */
@FunctionalInterface
public interface ProductInventoryItemResolver {
    ItemId resolve(String tenantId, ProductId productId);
}