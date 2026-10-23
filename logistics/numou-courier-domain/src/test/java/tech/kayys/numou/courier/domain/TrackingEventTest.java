package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TrackingEventTest {
    private static TrackingEvent event(Instant occurred, Instant recorded) {
        return new TrackingEvent(TrackingEventId.generate(), TenantId.generate(), ShipmentId.generate(), null,
                TrackingEventType.ARRIVED_AT_HUB, occurred, recorded, " hub-a ", "scanner-7",
                "handheld", "scan-001", null);
    }

    @Test
    void normalizesOperationalMetadataAndKeepsEventImmutable() {
        Instant occurred = Instant.parse("2026-10-24T01:00:00Z");
        TrackingEvent event = event(occurred, occurred.plusSeconds(2));
        assertEquals("HUB-A", event.locationCode());
        assertEquals("HANDHELD", event.source());
        assertEquals("scanner-7", event.actorReference());
        assertEquals(TrackingEventType.ARRIVED_AT_HUB, event.type());
    }

    @Test
    void rejectsServerRecordingBeforeOccurredTime() {
        Instant occurred = Instant.parse("2026-10-24T01:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> event(occurred, occurred.minusSeconds(1)));
    }

    @Test
    void requiresAnIdempotencyKeyAndSource() {
        Instant now = Instant.parse("2026-10-24T01:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new TrackingEvent(TrackingEventId.generate(),
                TenantId.generate(), ShipmentId.generate(), null, TrackingEventType.PICKED_UP, now, now,
                null, null, "scanner", " ", null));
    }
}
