package tech.kayys.numou.asset.application.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Contract tests for the versioned integration envelope (ASSET-28 §7, §44).
 */
@DisplayName("Asset event envelope factory")
class AssetEventEnvelopeFactoryTest {

    private record SampleEvent(UUID eventId, Instant occurredAt, String eventType) implements DomainEvent {
    }

    @Test
    @DisplayName("wraps the event with tenant, aggregate, version and correlation metadata")
    void buildsVersionedEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Instant at = Instant.parse("2026-02-05T09:00:00Z");
        DomainEvent event = new SampleEvent(eventId, at, "asset.asset-activated");

        EventEnvelope envelope = AssetEventEnvelopeFactory.of(
                "tenant-a", assetId, event, "corr-1", "cause-1");

        assertEquals(eventId, envelope.eventId());
        assertEquals("tenant-a", envelope.tenantId());
        assertEquals("asset.asset-activated", envelope.eventType());
        assertEquals("v1", envelope.schemaVersion());
        assertEquals("Asset", envelope.aggregateType());
        assertEquals(assetId, envelope.aggregateId());
        assertEquals(at, envelope.occurredAt());
        assertEquals("corr-1", envelope.correlationId());
        assertEquals("cause-1", envelope.causationId());
        assertSame(event, envelope.payload());
    }
}