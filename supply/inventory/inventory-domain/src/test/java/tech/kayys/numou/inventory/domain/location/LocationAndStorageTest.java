package tech.kayys.numou.inventory.domain.location;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.inventory.domain.inventory.Warehouse;
import tech.kayys.numou.inventory.domain.inventory.WarehouseId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LocationAndStorageTest {

    @Test
    void testGeoPointValidation() {
        GeoPoint valid = GeoPoint.of(-6.2088, 106.8456);
        assertEquals(new BigDecimal("-6.2088"), valid.latitude());
        assertEquals(new BigDecimal("106.8456"), valid.longitude());

        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(95.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(10.0, 190.0));
    }

    @Test
    void testStorageLocationCreationAndSelfParentRejection() {
        WarehouseId whId = WarehouseId.generate();
        StorageLocationId aisleId = StorageLocationId.generate();

        StorageLocation aisle = new StorageLocation(
                aisleId, whId, null, "AISLE-01", "Main Aisle",
                StorageLocationType.AISLE, StorageLocationStatus.ACTIVE
        );
        assertEquals("AISLE-01", aisle.code());

        // Self parent rejected
        assertThrows(IllegalArgumentException.class, () ->
                new StorageLocation(
                        aisleId, whId, aisleId, "AISLE-01", "Main Aisle",
                        StorageLocationType.AISLE, StorageLocationStatus.ACTIVE
                )
        );
    }

    @Test
    void testWarehouseEvolutionBackwardCompatibility() {
        WarehouseId whId = WarehouseId.generate();
        Warehouse legacy = new Warehouse(whId, "WH-1", "Central Depot", "Jakarta");
        assertEquals("WH-1", legacy.code());
        assertEquals("Jakarta", legacy.location());
        assertNull(legacy.siteId());
        assertNull(legacy.facilityId());

        SiteId siteId = SiteId.of("SITE-JKT");
        FacilityId facilityId = FacilityId.of("FAC-01");
        Warehouse governed = new Warehouse(whId, "WH-2", "Secondary Depot", siteId, facilityId);
        assertEquals("WH-2", governed.code());
        assertEquals(siteId, governed.siteId());
        assertEquals(facilityId, governed.facilityId());
    }
}
