package tech.kayys.numou.fulfillment.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.fulfillment.domain.FulfillmentLine;
import tech.kayys.numou.fulfillment.domain.FulfillmentStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentApplicationServiceTest {
    @Test
    void request_retry_returns_original_order_and_emits_only_one_outbox_event() {
        var repository = new InMemoryFulfillmentRepository();
        var service = new FulfillmentApplicationService(repository);
        var lines = List.of(FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("2.00"), "EA"));

        var first = service.request("tenant-a", new FulfillmentCommandKey("request-1"), "ORDER-1", lines);
        var replay = service.request("tenant-a", new FulfillmentCommandKey("request-1"), "ORDER-1",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("2.0"), "EA")));

        assertEquals(first.id(), replay.id());
        assertEquals(1, repository.outbox().size());
        assertTrue(repository.outbox().get(0).payload().contains("\"itemReference\":\"SKU-1\""));
    }

    @Test
    void rejects_same_idempotency_key_with_different_payload() {
        var service = new FulfillmentApplicationService(new InMemoryFulfillmentRepository());
        service.request("tenant-a", new FulfillmentCommandKey("request-2"), "ORDER-2",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", BigDecimal.ONE, "EA")));

        assertThrows(FulfillmentIdempotencyConflictException.class, () -> service.request("tenant-a",
                new FulfillmentCommandKey("request-2"), "ORDER-2",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("2"), "EA"))));
    }

    @Test
    void tenant_scope_prevents_cross_tenant_order_reads() {
        var service = new FulfillmentApplicationService(new InMemoryFulfillmentRepository());
        var order = service.request("tenant-a", new FulfillmentCommandKey("request-3"), "ORDER-3",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", BigDecimal.ONE, "EA")));

        assertThrows(IllegalArgumentException.class, () -> service.confirmAllocation("tenant-b", order.id(),
                "line-1", BigDecimal.ONE, "WH-1", new FulfillmentCommandKey("allocate-1")));
    }

    @Test
    void allocation_confirmation_is_idempotent_and_outbox_tracks_transition() {
        var repository = new InMemoryFulfillmentRepository();
        var service = new FulfillmentApplicationService(repository);
        var order = service.request("tenant-a", new FulfillmentCommandKey("request-4"), "ORDER-4",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("4"), "EA")));

        var allocated = service.confirmAllocation("tenant-a", order.id(), "line-1", new BigDecimal("4"),
                "WH-1", new FulfillmentCommandKey("allocate-4"));
        var replay = service.confirmAllocation("tenant-a", order.id(), "line-1", new BigDecimal("4.0"),
                "WH-1", new FulfillmentCommandKey("allocate-4"));

        assertEquals(FulfillmentStatus.ALLOCATED, allocated.status());
        assertEquals(allocated, replay);
        assertEquals(2, repository.outbox().size());
        assertEquals("fulfillment.state-changed.v1", repository.outbox().get(1).eventType());
    }

    @Test
    void shipment_handoff_is_emitted_before_shipped_state() {
        var repository = new InMemoryFulfillmentRepository();
        var service = new FulfillmentApplicationService(repository);
        var order = service.request("tenant-a", new FulfillmentCommandKey("request-5"), "ORDER-5",
                List.of(FulfillmentLine.requested("line-1", "SKU-1", BigDecimal.ONE, "EA")));
        order = service.confirmAllocation("tenant-a", order.id(), "line-1", BigDecimal.ONE,
                "WH-1", new FulfillmentCommandKey("allocate-5"));
        order = service.confirmPick("tenant-a", order.id(), "line-1", BigDecimal.ONE,
                new FulfillmentCommandKey("pick-5"));
        order = service.confirmPack("tenant-a", order.id(), "line-1", BigDecimal.ONE,
                new FulfillmentCommandKey("pack-5"));
        order = service.stage("tenant-a", order.id(), new FulfillmentCommandKey("stage-5"));

        order = service.requestShipmentHandoff("tenant-a", order.id(), "SHIP-5",
                new FulfillmentCommandKey("handoff-5"));
        assertEquals(FulfillmentStatus.HANDOFF_REQUESTED, order.status());
        assertTrue(repository.outbox().stream().anyMatch(e ->
                e.eventType().equals("fulfillment.shipment-handoff-requested.v1")));
        order = service.confirmShipped("tenant-a", order.id(), "SHIP-5",
                new FulfillmentCommandKey("shipped-5"));
        assertEquals(FulfillmentStatus.SHIPPED, order.status());
    }
}
