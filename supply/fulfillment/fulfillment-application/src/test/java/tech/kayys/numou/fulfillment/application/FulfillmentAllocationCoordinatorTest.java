package tech.kayys.numou.fulfillment.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.fulfillment.domain.FulfillmentLine;
import tech.kayys.numou.fulfillment.domain.FulfillmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentAllocationCoordinatorTest {
    @Test
    void reserves_all_lines_before_confirming_allocation_and_completes_saga() {
        var repository = new InMemoryFulfillmentRepository();
        var service = new FulfillmentApplicationService(repository);
        var inventory = new InMemoryInventoryReservationGateway();
        var sagas = new InMemoryFulfillmentAllocationSagaRepository();
        var coordinator = new FulfillmentAllocationCoordinator(service, repository, inventory, sagas);
        var order = service.request("tenant-a", new FulfillmentCommandKey("request-alloc-1"), "ORDER-A",
                List.of(
                        FulfillmentLine.requested("line-1", "SKU-1", new BigDecimal("2"), "EA"),
                        FulfillmentLine.requested("line-2", "SKU-2", new BigDecimal("3"), "EA")));

        var allocated = coordinator.allocate("tenant-a", order.id(), "WH-1",
                Instant.now().plusSeconds(3600), new FulfillmentCommandKey("workflow-alloc-1"));

        assertEquals(FulfillmentStatus.ALLOCATED, allocated.status());
        assertEquals(2, inventory.reservationCount());
        assertEquals(0, inventory.releasedCount());
        assertEquals(AllocationSagaStatus.COMPLETED,
                sagas.find("tenant-a", "workflow-alloc-1").orElseThrow().status());
    }

    @Test
    void compensates_prior_reservations_when_a_later_line_cannot_be_reserved() {
        var repository = new InMemoryFulfillmentRepository();
        var service = new FulfillmentApplicationService(repository);
        var inventory = new InMemoryInventoryReservationGateway();
        inventory.failForItem("SKU-FAIL");
        var sagas = new InMemoryFulfillmentAllocationSagaRepository();
        var coordinator = new FulfillmentAllocationCoordinator(service, repository, inventory, sagas);
        var order = service.request("tenant-a", new FulfillmentCommandKey("request-alloc-2"), "ORDER-B",
                List.of(
                        FulfillmentLine.requested("line-1", "SKU-OK", BigDecimal.ONE, "EA"),
                        FulfillmentLine.requested("line-2", "SKU-FAIL", BigDecimal.ONE, "EA")));

        assertThrows(IllegalStateException.class, () -> coordinator.allocate("tenant-a", order.id(), "WH-1",
                Instant.now().plusSeconds(3600), new FulfillmentCommandKey("workflow-alloc-2")));

        assertEquals(FulfillmentStatus.REQUESTED,
                repository.find("tenant-a", order.id()).orElseThrow().status());
        assertEquals(1, inventory.releasedCount());
        assertEquals(AllocationSagaStatus.COMPENSATED,
                sagas.find("tenant-a", "workflow-alloc-2").orElseThrow().status());
    }
}
