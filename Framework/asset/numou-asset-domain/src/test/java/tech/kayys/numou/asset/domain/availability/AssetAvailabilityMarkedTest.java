package tech.kayys.syirkah.asset.domain.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.asset.domain.event.AssetAvailabilityMarked;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Event-type contract for availability transitions (ASSET-26 §14). */
@DisplayName("AssetAvailabilityMarked event")
class AssetAvailabilityMarkedTest {

    private static AssetAvailabilityMarked event(AssetAvailabilityType type) {
        return new AssetAvailabilityMarked(
                UUID.randomUUID(), Instant.parse("2026-10-03T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), type,
                AssetAvailabilityReason.MAINTENANCE,
                Instant.parse("2026-10-03T10:00:00Z"), null, null);
    }

    @Test
    @DisplayName("unavailable produces asset.asset-unavailable")
    void unavailableEventType() {
        assertEquals("asset.asset-unavailable", event(AssetAvailabilityType.UNAVAILABLE).eventType());
    }

    @Test
    @DisplayName("available produces asset.asset-available")
    void availableEventType() {
        assertEquals("asset.asset-available", event(AssetAvailabilityType.AVAILABLE).eventType());
    }
}