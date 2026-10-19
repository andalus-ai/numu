package tech.kayys.numou.asset.application.timeline;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Outbound port for the asset timeline read model (ASSET-27 §30).
 *
 * <p>Append-only and idempotent: {@code appendIfAbsent} is a no-op when the
 * source event has already been projected, which makes at-least-once redelivery
 * and replay safe (§26, §29).</p>
 */
public interface AssetTimelineRepository {

    /** Appends the entry unless its {@code sourceEventId} is already present. */
    CompletionStage<Boolean> appendIfAbsent(AssetTimelineEntry entry);

    CompletionStage<Boolean> existsBySourceEvent(UUID sourceEventId);

    /** Deterministically ordered by {@code occurredAt}, then {@code sourceEventId}. */
    CompletionStage<List<AssetTimelineEntry>> findByAsset(String tenantId, UUID assetId);
}