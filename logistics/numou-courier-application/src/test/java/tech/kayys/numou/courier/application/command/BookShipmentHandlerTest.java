package tech.kayys.numou.courier.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookShipmentHandlerTest {
    @Test
    void booksAllParcelsBeforePersistingAggregate() {
        ShipmentRepository repository = mock(ShipmentRepository.class);
        when(repository.save(any(), any())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(invocation.getArgument(1)));
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        ShipmentAddress sender = new ShipmentAddress("Sender", "+62111", "Origin", null, "Bandung", null, null, "ID");
        ShipmentAddress recipient = new ShipmentAddress("Recipient", "+62222", "Destination", null, "Jakarta", null, null, "ID");
        List<Parcel> parcels = List.of(parcel("P-001"), parcel("P-002"));
        BookShipmentCommand command = new BookShipmentCommand(tenant, ShipmentId.generate(), "SHIP-1001",
                sender, recipient, ServiceLevel.EXPRESS, parcels, now);

        Shipment result = new BookShipmentHandler(repository).handle(command).toCompletableFuture().join();

        assertEquals(ShipmentStatus.BOOKED, result.status());
        assertEquals(2, result.parcels().size());
        verify(repository).save(tenant, result);
    }

    @Test
    void rejectsEmptyParcelListBeforePersistence() {
        ShipmentRepository repository = mock(ShipmentRepository.class);
        assertThrows(IllegalArgumentException.class, () -> new BookShipmentCommand(
                TenantId.generate(), ShipmentId.generate(), "SHIP-1002",
                new ShipmentAddress("A", "1", "A", null, "A", null, null, "ID"),
                new ShipmentAddress("B", "2", "B", null, "B", null, null, "ID"),
                ServiceLevel.STANDARD, List.of(), Instant.now()));
        verifyNoInteractions(repository);
    }

    private static Parcel parcel(String reference) {
        return new Parcel(ParcelId.generate(), reference, new BigDecimal("1.25"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"), null);
    }
}