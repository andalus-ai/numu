package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourierOutboxPublicationContractTest {
    @Test
    void eventEnvelopeRequiresStableIdentityAndPayload() {
        UUID tenant = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CourierPublishedEvent event = new CourierPublishedEvent(
                tenant, eventId, "courier.shipment.delivered.v1", "shipment", "SHIP-1",
                Instant.parse("2026-01-01T00:00:00Z"), "{\"shipmentId\":\"SHIP-1\"}");

        assertEquals(tenant, event.tenantId());
        assertEquals(eventId, event.eventId());
        assertThrows(IllegalArgumentException.class, () -> new CourierPublishedEvent(
                tenant, eventId, " ", "shipment", "SHIP-1", Instant.EPOCH, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new CourierPublishedEvent(
                tenant, eventId, "event.v1", "shipment", "SHIP-1", Instant.EPOCH, " "));
    }

    @Test
    void migrationSeparatesDestinationAcknowledgementFromProjectionMarker() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V25__courier_outbox_publication_delivery.sql"),
                StandardCharsets.UTF_8);
        String relay = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcCourierOutboxPublisher.java"),
                StandardCharsets.UTF_8);

        assertTrue(migration.contains("PRIMARY KEY (tenant_id, event_id, destination)"));
        assertTrue(migration.contains("FOREIGN KEY (tenant_id, event_id) REFERENCES courier_event_outbox"));
        assertTrue(migration.contains("lease_until"));
        assertTrue(migration.contains("status IN ('PENDING', 'PUBLISHED', 'DEAD')"));
        assertTrue(relay.contains("FOR UPDATE OF p SKIP LOCKED"));
        assertTrue(relay.contains("lease_until < now()"));
        assertTrue(relay.contains("MAX_ATTEMPTS = 12"));
        assertTrue(relay.contains("published_at = now()"));
        assertFalse(relay.contains("SET published_at = now() WHERE tenant_id = ? AND id = ? AND published_at IS NULL"));
    }
}
