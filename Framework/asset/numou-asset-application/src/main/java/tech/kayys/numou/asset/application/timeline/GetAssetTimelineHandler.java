package tech.kayys.numou.asset.application.timeline;

import io.smallrye.mutiny.Uni;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Read-side handler for the unified asset timeline (ASSET-27 §3, §29).
 *
 * <p>Ordering is deterministic: {@code occurredAt} first, then {@code sourceEventId}
 * to break ties — so replays and out-of-order delivery still yield a stable,
 * reproducible timeline.</p>
 */
public class GetAssetTimelineHandler {

    private static final Comparator<AssetTimelineEntry> ORDER = Comparator
            .comparing(AssetTimelineEntry::occurredAt)
            .thenComparing(AssetTimelineEntry::sourceEventId);

    private final AssetTimelineRepository timeline;

    public GetAssetTimelineHandler(AssetTimelineRepository timeline) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
    }

    public Uni<AssetTimelinePage> handle(GetAssetTimelineQuery query) {
        AssetTimelineFilter filter = query.filter() == null ? AssetTimelineFilter.all() : query.filter();
        return Uni.createFrom()
                .completionStage(() -> timeline.findByAsset(query.tenantId(), query.assetId()))
                .map(entries -> paginate(entries, filter));
    }

    static AssetTimelinePage paginate(List<AssetTimelineEntry> entries, AssetTimelineFilter filter) {
        List<AssetTimelineEntry> filtered = entries.stream()
                .filter(filter::accepts)
                .sorted(ORDER)
                .toList();
        long total = filtered.size();
        int fromIndex = Math.min(filter.page() * filter.size(), filtered.size());
        int toIndex = Math.min(fromIndex + filter.size(), filtered.size());
        return AssetTimelinePage.of(filtered.subList(fromIndex, toIndex), filter.page(), filter.size(), total);
    }
}