package tech.kayys.numou.asset.application.timeline;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable entry in the unified asset history (ASSET-27 §4).
 *
 * <p>The timeline is a read model, not an event store and not an audit log. Each
 * entry preserves the identity of the originating business event
 * ({@code sourceEventId}, {@code eventType}) so a timeline entry can always be
 * traced back to the operation that produced it (§6).</p>
 */
public record AssetTimelineEntry(
        UUID id,
        String tenantId,
        UUID assetId,
        Instant occurredAt,
        String eventType,
        AssetTimelineCategory category,
        String title,
        String description,
        String source,
        String sourceReference,
        UUID sourceEventId
) {

    public AssetTimelineEntry {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId cannot be null");
    }
}