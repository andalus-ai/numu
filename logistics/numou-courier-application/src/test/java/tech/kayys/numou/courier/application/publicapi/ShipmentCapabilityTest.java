package tech.kayys.numou.courier.application.publicapi;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.command.BookShipmentHandler;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShipmentCapabilityTest {
    @Test
    void lookupAlwaysPassesExplicitTenantToRepository() {
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        TenantId tenant = TenantId.generate();
        ShipmentId shipmentId = ShipmentId.generate();
        when(shipments.findById(tenant, shipmentId)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        ShipmentCapability capability = new ShipmentCapability(mock(BookShipmentHandler.class), shipments,
                mock(TrackingEventRepository.class));

        Optional<ShipmentCapability.ShipmentView> result = capability
                .findById(tenant.toString(), shipmentId.toString()).toCompletableFuture().join();

        assertTrue(result.isEmpty());
        verify(shipments).findById(tenant, shipmentId);
        verifyNoMoreInteractions(shipments);
    }

    @Test
    void trackingPassesBothCursorFieldsAndMapsImmutableEventViews() {
        TrackingEventRepository tracking = mock(TrackingEventRepository.class);
        TenantId tenant = TenantId.generate();
        ShipmentId shipment = ShipmentId.generate();
        Instant occurredAt = Instant.parse("2026-10-27T02:00:00Z");
        TrackingEventId eventId = TrackingEventId.generate();
        TrackingCursor cursor = new TrackingCursor(occurredAt, eventId);
        TrackingEvent event = new TrackingEvent(eventId, tenant, shipment, null,
                TrackingEventType.ARRIVED_AT_HUB, occurredAt, occurredAt, "BDO", "actor-1",
                "SCANNER", "scan-001", null);
        when(tracking.findByShipment(tenant, shipment, cursor, 20))
                .thenReturn(CompletableFuture.completedFuture(List.of(event)));
        ShipmentCapability capability = new ShipmentCapability(mock(BookShipmentHandler.class),
                mock(ShipmentRepository.class), tracking);

        ShipmentCapability.TrackingPage page = capability.trackingTimeline(tenant.toString(), shipment.toString(),
                new ShipmentCapability.TrackingPosition(occurredAt, eventId.toString()), 20)
                .toCompletableFuture().join();

        assertEquals(1, page.events().size());
        assertEquals(eventId.toString(), page.events().get(0).eventId());
        assertEquals("ARRIVED_AT_HUB", page.events().getFirst().type());
        assertEquals(new ShipmentCapability.TrackingPosition(occurredAt, eventId.toString()), page.nextBefore());
        verify(tracking).findByShipment(tenant, shipment, cursor, 20);
    }

    @Test
    void rejectsUnboundedTrackingPageSize() {
        ShipmentCapability capability = new ShipmentCapability(mock(BookShipmentHandler.class),
                mock(ShipmentRepository.class), mock(TrackingEventRepository.class));
        assertThrows(IllegalArgumentException.class, () -> capability.trackingTimeline(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, 101));
    }
}
