package tech.kayys.numou.courier.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignDeliveryTaskHandlerTest {
    @Test
    void onlyAssignsBookedShipment() {
        TenantId tenant = TenantId.generate();
        ShipmentId shipmentId = ShipmentId.generate();
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        DeliveryTaskRepository tasks = mock(DeliveryTaskRepository.class);
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        Shipment draft = Shipment.draft(shipmentId, tenant, "SHIP-ASSIGN",
                new ShipmentAddress("Sender", "+62811111111", "Jl A", null, "Bandung", "West Java", "40111", "ID"),
                new ShipmentAddress("Recipient", "+62822222222", "Jl B", null, "Bandung", "West Java", "40112", "ID"),
                ServiceLevel.STANDARD, now);
        when(shipments.findById(tenant, shipmentId)).thenReturn(CompletableFuture.completedFuture(Optional.of(draft)));
        AssignDeliveryTaskHandler handler = new AssignDeliveryTaskHandler(shipments, tasks);
        CompletionException failure = assertThrows(CompletionException.class, () -> handler.handle(new AssignDeliveryTaskCommand(
                tenant, DeliveryTaskId.generate(), shipmentId, "courier-1", "dispatcher-1", now)).toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        verifyNoInteractions(tasks);
    }
}
