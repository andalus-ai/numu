package tech.kayys.numou.inventory.domain.inventory;

import tech.kayys.numou.inventory.domain.location.FacilityId;
import tech.kayys.numou.inventory.domain.location.SiteId;

import java.util.Objects;

/** 
 * Physical or logical storage warehouse facility.
 * Retains backward-compatible constructor for (id, code, name, location).
 */
public record Warehouse(
        WarehouseId id, 
        String code, 
        String name, 
        String location,
        SiteId siteId,
        FacilityId facilityId
) {
    public Warehouse {
        Objects.requireNonNull(id, "WarehouseId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        code = code.trim().toUpperCase();
        name = name.trim();

        if (code.isBlank()) {
            throw new IllegalArgumentException("Warehouse code cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Warehouse name cannot be blank");
        }
    }

    /**
     * Backward-compatible constructor accepting free-form location string.
     */
    public Warehouse(WarehouseId id, String code, String name, String location) {
        this(id, code, name, location, null, null);
    }

    /**
     * Governed constructor with typed SiteId and FacilityId references.
     */
    public Warehouse(WarehouseId id, String code, String name, SiteId siteId, FacilityId facilityId) {
        this(id, code, name, null, siteId, facilityId);
    }
}
