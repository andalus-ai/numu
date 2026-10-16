package tech.kayys.numou.asset.interfaces.consumer;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.application.timeline.AssetEventAssetId;
import tech.kayys.numou.asset.application.timeline.AssetTimelineEntry;
import tech.kayys.numou.asset.application.timeline.AssetTimelineProjectionPolicy;
import tech.kayys.numou.asset.application.timeline.AssetTimelineRepository;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Projects business events into the unified asset timeline (ASSET-27 §3, §26).
 *
 * <p>The consumer only reads events; it never mutates {@code Asset}. Projection
 * is idempotent and replayable — re-delivering an event whose
 * {@code sourceEventId} is already present is a no-op, so at-least-once delivery
 * and out-of-order redelivery are both safe (§29).</p>
 */
@ApplicationScoped
public class AssetTimelineProjector {

    private final AssetTimelineRepository timeline;

    public AssetTimelineProjector(AssetTimelineRepository timeline) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
    }

    /** Projects a single event; non-asset-scoped events are ignored. */
    public Uni<Void> project(String tenantId, DomainEvent event) {
        if (tenantId == null || tenantId.isBlank() || event == null) {
            return Uni.createFrom().nullItem();
        }
        UUID assetId = AssetEventAssetId.resolve(event);
        if (assetId == null) {
            return Uni.createFrom().nullItem();
        }
        AssetTimelineEntry entry = AssetTimelineProjectionPolicy.project(tenantId, assetId, event);
        if (entry == null) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom()
                .completionStage(() -> timeline.appendIfAbsent(entry))
                .replaceWith((Void) null);
    }
}