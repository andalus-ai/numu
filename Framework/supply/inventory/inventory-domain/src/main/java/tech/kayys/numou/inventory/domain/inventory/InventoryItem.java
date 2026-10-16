package tech.kayys.syirkah.inventory.domain.inventory;

import java.util.Objects;

/**
 * Root definition of an inventory catalog item.
 */
public record InventoryItem(
        ItemId id,
        String sku,
        String name,
        ValuationMethod valuationMethod,
        TrackingMethod trackingMethod,
        String uom
) {
    public InventoryItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sku);
        Objects.requireNonNull(name);
        Objects.requireNonNull(valuationMethod);
        Objects.requireNonNull(trackingMethod);
        Objects.requireNonNull(uom);
    }
}
