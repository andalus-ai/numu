package tech.kayys.numou.courier.application.publicapi;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.application.command.BookShipmentHandler;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
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
    void bookingDelegatesToExistingHandlerAndPersistsShipmentWithAllParcels() {
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-10-27T02:00:00Z");
        when(shipments.save(eq(tenant), any(Shipment.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(1)));
        ShipmentCapability capability = new ShipmentCapability(new BookShipmentHandler(shipments), shipments,
                mock(TrackingEventRepository.class));

        ShipmentCapability.ShipmentView result = capability.book(new ShipmentCapability.BookRequest(
                tenant.toString(), null, "ORD-001",
                new ShipmentCapability.Address("Sender", "+628111111111", "Street 1", null,
                        "Bandung", "West Java", "40111", "ID"),
                new ShipmentCapability.Address("Recipient", "+628222222222", "Street 2", null,
                        "Jakarta", "DKI Jakarta", "10110", "ID"),
                "express",
                List.of(
                        new ShipmentCapability.ParcelInput(null, "BOX-001", new BigDecimal("1.2"),
                                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"), null),
                        new ShipmentCapability.ParcelInput(null, "BOX-002", new BigDecimal("2.5"),
                                new BigDecimal("15"), new BigDecimal("20"), new BigDecimal("25"), "Keep upright")
                ), now)).toCompletableFuture().join();

        assertEquals(tenant.toString(), result.tenantId());
        assertEquals("ORD-001", result.reference());
        assertEquals("BOOKED", result.status());
        assertEquals("EXPRESS", result.serviceLevel());
        assertEquals(2, result.parcels().size());
        assertEquals(now, result.bookedAt());
        verify(shipments).save(eq(tenant), argThat(saved ->
                saved.tenantId().equals(tenant)
                        && saved.status() == ShipmentStatus.BOOKED
                        && saved.parcels().size() == 2
                        && saved.parcels().get(0).reference().equals("BOX-001")
                        && saved.parcels().get(1).reference().equals("BOX-002")));
    }

    @Test
    void lookupAlwaysPassesExplicitTenantToRepository() {
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        TenantId tenant = TenantId.generate();
        ShipmentId shipmentId = ShipmentId.generate();
        when(shipments.findById(tenant, shipmentId)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        ShipmentCapability capability = new ShipmentCapability(new BookShipmentHandler(mock(ShipmentRepository.class)),
                shipments, mock(TrackingEventRepository.class));

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
        ShipmentCapability capability = new ShipmentCapability(new BookShipmentHandler(mock(ShipmentRepository.class)),
                mock(ShipmentRepository.class), tracking);

        ShipmentCapability.TrackingPage page = capability.trackingTimeline(tenant.toString(), shipment.toString(),
                new ShipmentCapability.TrackingPosition(occurredAt, eventId.toString()), 20)
                .toCompletableFuture().join();

        assertEquals(1, page.events().size());
        assertEquals(eventId.toString(), page.events().get(0).eventId());
        assertEquals("ARRIVED_AT_HUB", page.events().get(0).type());
        assertEquals(new ShipmentCapability.TrackingPosition(occurredAt, eventId.toString()), page.nextBefore());
        verify(tracking).findByShipment(tenant, shipment, cursor, 20);
    }

    @Test
    void rejectsUnboundedTrackingPageSize() {
        ShipmentCapability capability = new ShipmentCapability(new BookShipmentHandler(mock(ShipmentRepository.class)),
                mock(ShipmentRepository.class), mock(TrackingEventRepository.class));
        assertThrows(IllegalArgumentException.class, () -> capability.trackingTimeline(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, 101));
    }

    @Test
    void rejectsBookingWithoutParcelsBeforeCallingRepository() {
        ShipmentRepository shipments = mock(ShipmentRepository.class);
        ShipmentCapability capability = new ShipmentCapability(new BookShipmentHandler(shipments), shipments,
                mock(TrackingEventRepository.class));

        assertThrows(IllegalArgumentException.class, () -> new ShipmentCapability.BookRequest(
                UUID.randomUUID().toString(), null, "ORD-001",
                new ShipmentCapability.Address("Sender", "+628111111111", "Street 1", null,
                        "Bandung", null, null, "ID"),
                new ShipmentCapability.Address("Recipient", "+628222222222", "Street 2", null,
                        "Jakarta", null, null, "ID"),
                "STANDARD", List.of(), Instant.parse("2026-10-27T02:00:00Z")));
        verifyNoInteractions(shipments);
    }
}
