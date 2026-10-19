package tech.kayys.numou.asset.application.timeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Projection and read-model tests for the asset timeline (ASSET-27 acceptance:
 * category filtering, deterministic ordering, source traceability).
 */
@DisplayName("Asset timeline projection")
class AssetTimelineProjectionPolicyTest {

    private static final UUID ASSET = UUID.randomUUID();

    private static DomainEvent event(String type, Instant at, UUID eventId) {
        return new TestEvent(eventId, at, type);
    }

    @Test
    @DisplayName("event types map to timeline categories")
    void mapsCategories() {
        assertEquals(AssetTimelineCategory.LIFECYCLE,
                AssetTimelineProjectionPolicy.categoryOf("asset.asset-activated"));
        assertEquals(AssetTimelineCategory.MAINTENANCE,
                AssetTimelineProjectionPolicy.categoryOf("maintenance.work-order-completed"));
        assertEquals(AssetTimelineCategory.WARRANTY,
                AssetTimelineProjectionPolicy.categoryOf("asset.warranty-created"));
        assertEquals(AssetTimelineCategory.FINANCIAL,
                AssetTimelineProjectionPolicy.categoryOf("accounting.fixed-asset-capitalized"));
        assertEquals(AssetTimelineCategory.AVAILABILITY,
                AssetTimelineProjectionPolicy.categoryOf("asset.asset-unavailable"));
        assertEquals(AssetTimelineCategory.UTILIZATION,
                AssetTimelineProjectionPolicy.categoryOf("asset.utilization-recorded"));
    }

    @Test
    @DisplayName("an unmapped event is not projected onto the timeline")
    void ignoresUnmappedEvent() {
        assertNull(AssetTimelineProjectionPolicy.categoryOf("something.unrelated"));
        assertNull(AssetTimelineProjectionPolicy.project(
                "tenant-a", ASSET, event("something.unrelated", Instant.now(), UUID.randomUUID())));
    }

    @Test
    @DisplayName("a timeline entry preserves the source event identity")
    void preservesSourceTraceability() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-02-05T09:00:00Z");
        AssetTimelineEntry entry = AssetTimelineProjectionPolicy.project(
                "tenant-a", ASSET, event("asset.asset-activated", occurredAt, eventId));

        assertEquals("asset.asset-activated", entry.eventType());
        assertEquals(AssetTimelineCategory.LIFECYCLE, entry.category());
        assertEquals("Asset activated", entry.title());
        assertEquals("asset", entry.source());
        assertEquals(eventId, entry.sourceEventId());
        assertEquals(occurredAt, entry.occurredAt());
    }

    @Test
    @DisplayName("the read model orders deterministically and filters by category")
    void ordersAndFilters() {
        UUID e1 = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID e2 = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        Instant sameInstant = Instant.parse("2026-02-06T00:00:00Z");
        List<AssetTimelineEntry> entries = List.of(
                AssetTimelineProjectionPolicy.project("t", ASSET,
                        event("maintenance.work-order-completed", Instant.parse("2026-02-07T00:00:00Z"), e2)),
                AssetTimelineProjectionPolicy.project("t", ASSET,
                        event("asset.asset-created", sameInstant, e2)),
                AssetTimelineProjectionPolicy.project("t", ASSET,
                        event("asset.asset-activated", sameInstant, e1)));

        AssetTimelinePage page = GetAssetTimelineHandler.paginate(
                entries, new AssetTimelineFilter(null, null, null, 0, 50));
        assertEquals(3, page.total());
        // same occurredAt -> ordered by sourceEventId (e1 before e2)
        assertEquals("asset.asset-activated", page.items().get(0).eventType());
        assertEquals("maintenance.work-order-completed", page.items().get(2).eventType());

        AssetTimelinePage maintenanceOnly = GetAssetTimelineHandler.paginate(
                entries, new AssetTimelineFilter(Set.of(AssetTimelineCategory.MAINTENANCE), null, null, 0, 50));
        assertEquals(1, maintenanceOnly.total());
        assertTrue(maintenanceOnly.items().get(0).eventType().startsWith("maintenance."));
    }

    @Test
    @DisplayName("filter window excludes entries outside the range")
    void filtersByWindow() {
        Instant at = Instant.parse("2026-02-07T00:00:00Z");
        List<AssetTimelineEntry> entries = List.of(AssetTimelineProjectionPolicy.project(
                "t", ASSET, event("asset.asset-activated", at, UUID.randomUUID())));
        AssetTimelinePage page = GetAssetTimelineHandler.paginate(entries,
                new AssetTimelineFilter(null, Instant.parse("2026-03-01T00:00:00Z"), null, 0, 50));
        assertEquals(0, page.total());
    }

    private record TestEvent(UUID eventId, Instant occurredAt, String eventType) implements DomainEvent {
    }
}