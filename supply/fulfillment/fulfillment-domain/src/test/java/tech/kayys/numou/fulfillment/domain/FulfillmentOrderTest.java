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
        order = order.stage().requestShipmentHandoff("SHIP-1");
        assertEquals(FulfillmentStatus.HANDOFF_REQUESTED, order.status());
        order = order.ship("SHIP-1").deliver();

        assertEquals(FulfillmentStatus.DELIVERED, order.status());
        assertEquals("SHIP-1", order.shipmentReference());
        assertEquals(8, order.version());
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

        // Pack is a lifecycle operation allowed only once all allocated quantity has been picked.
        // Exercise the line quantity invariant after satisfying that lifecycle precondition.
        var picked = order.pick("line-1", new BigDecimal("3"));
        assertThrows(IllegalArgumentException.class, () -> picked.pack("line-1", new BigDecimal("4")));
    }

    @Test
    void prevents_handoff_before_packing_and_requires_matching_shipment_reference() {
        var order = FulfillmentOrder.request("tenant-a", "ORDER-3", List.of(
                FulfillmentLine.requested("line-1", "SKU-3", BigDecimal.ONE, "EA")));
        assertThrows(IllegalStateException.class, () -> order.requestShipmentHandoff("SHIP-3"));
        var staged = order.allocate("line-1", BigDecimal.ONE).pick("line-1", BigDecimal.ONE)
                .pack("line-1", BigDecimal.ONE).stage().requestShipmentHandoff("SHIP-3");
        assertThrows(IllegalArgumentException.class, () -> staged.ship("SHIP-OTHER"));
        assertThrows(IllegalStateException.class, staged::cancel);
    }

    @Test
    void prevents_duplicate_line_ids() {
        var requested = FulfillmentLine.requested("line-1", "SKU-4", BigDecimal.ONE, "EA");
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
                .requestShipmentHandoff("SHIP-5")
                .ship("SHIP-5");

        assertThrows(IllegalStateException.class, order::cancel);
    }
}