package tech.kayys.numou.fulfillment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentOrderTest {
    @Test
    void enforces_quantity_progression_and_explicit_handoff_lifecycle() {
        var order = FulfillmentOrder.request("tenant-a", "ORDER-1", List.of(
                FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("5"), "EA")));

        order = order.allocate("line-1", new BigDecimal("5"));
        assertEquals(FulfillmentStatus.ALLOCATED, order.status());
        order = order.pick("line-1", new BigDecimal("5"));
        assertEquals(FulfillmentStatus.PICKED, order.status());
        order = order.pack("line-1", new BigDecimal("5"));
        assertEquals(FulfillmentStatus.PACKED, order.status());
        order = order.stage().ship("SHIP-1").deliver();

        assertEquals(FulfillmentStatus.DELIVERED, order.status());
        assertEquals("SHIP-1", order.shipmentReference());
        assertEquals(7, order.version());
    }

    @Test
    void supports_fulfilling_allocated_quantity_while_remainder_is_backordered() {
        var order = FulfillmentOrder.request("tenant-a", "ORDER-PARTIAL", List.of(
                FulfillmentLine.requested("line-1", "SKU-P", new BigDecimal("5"), "EA")))
                .allocate("line-1", new BigDecimal("3"));

        assertEquals(FulfillmentStatus.PARTIALLY_ALLOCATED, order.status());
        order = order.pick("line-1", new BigDecimal("3"));
        assertEquals(FulfillmentStatus.PICKED, order.status());
        assertEquals(new BigDecimal("5"), order.lines().get(0).requestedQuantity());
        assertEquals(new BigDecimal("3"), order.lines().get(0).pickedQuantity());
    }

    @Test
    void rejects_picking_more_than_allocated_and_packing_more_than_picked() {
        var order = FulfillmentOrder.request("tenant-a", "ORDER-2", List.of(
                FulfillmentLine.requested("line-1", "SKU-2", new BigDecimal("5"), "EA")))
                .allocate("line-1", new BigDecimal("3"));

        assertThrows(IllegalArgumentException.class, () -> order.pick("line-1", new BigDecimal("4")));
        var picked = order.pick("line-1", new BigDecimal("2"));
        assertThrows(IllegalArgumentException.class, () -> picked.pack("line-1", new BigDecimal("3")));
    }

    @Test
    void prevents_shipment_handoff_before_packing_and_duplicate_line_ids() {
        var requested = FulfillmentLine.requested("line-1", "SKU-3", new BigDecimal("1"), "EA");
        var order = FulfillmentOrder.request("tenant-a", "ORDER-3", List.of(requested));
        assertThrows(IllegalStateException.class, () -> order.ship("SHIP-3"));
        assertThrows(IllegalArgumentException.class, () -> FulfillmentOrder.request("tenant-a", "ORDER-4",
                List.of(requested, requested)));
    }

    @Test
    void cancellation_is_rejected_after_shipment() {
        var order = FulfillmentOrder.request("tenant-a", "ORDER-5", List.of(
                FulfillmentLine.requested("line-1", "SKU-5", BigDecimal.ONE, "EA")))
                .allocate("line-1", BigDecimal.ONE)
                .pick("line-1", BigDecimal.ONE)
                .pack("line-1", BigDecimal.ONE)
                .stage()
                .ship("SHIP-5");

        assertThrows(IllegalStateException.class, order::cancel);
    }
}
