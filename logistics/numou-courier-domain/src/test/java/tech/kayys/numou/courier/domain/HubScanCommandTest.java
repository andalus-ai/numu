package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HubScanCommandTest {

    private final TenantId tenantId = TenantId.of(UUID.randomUUID());
    private final ShipmentId shipmentId = ShipmentId.of(UUID.randomUUID());
    private final Instant occurredAt = Instant.parse("2026-10-24T01:00:00Z");

    @Test
    void normalizesSourceAndHubAndPreservesStableIdempotencyKey() {
        HubScanCommand command = command(" scanner-a ", " hub-jkt ");

        assertEquals("SCANNER-A", command.source());
        assertEquals("HUB-JKT", command.hubReference());
        assertEquals("device-42:scan-19", command.idempotencyKey());
    }

    @Test
    void rejectsReceiptTimeBeforeOccurrenceTime() {
        assertThrows(IllegalArgumentException.class, () -> new HubScanCommand(
                tenantId, UUID.randomUUID(), "scanner", "key-1", "sha256:abc",
                shipmentId, "parcel-1", HubScanType.HUB_INTAKE, "hub-a", "operator-1",
                occurredAt, occurredAt.minusSeconds(1)));
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> new HubScanCommand(
                tenantId, UUID.randomUUID(), "scanner", " ", "sha256:abc",
                shipmentId, "parcel-1", HubScanType.HUB_INTAKE, "hub-a", "operator-1",
                occurredAt, occurredAt));
    }

    private HubScanCommand command(String source, String hub) {
        return new HubScanCommand(tenantId, UUID.randomUUID(), source, "device-42:scan-19",
                "sha256:abc", shipmentId, "parcel-1", HubScanType.HUB_INTAKE, hub,
                "operator-1", occurredAt, occurredAt.plusSeconds(2));
    }
}
