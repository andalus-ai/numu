package tech.kayys.numou.asset.application.timeline;

import java.util.List;

/** Page envelope for timeline queries (ASSET-27 §acceptance). */
public record AssetTimelinePage(List<AssetTimelineEntry> items, int page, int size, long total) {

    public static AssetTimelinePage of(List<AssetTimelineEntry> items, int page, int size, long total) {
        return new AssetTimelinePage(List.copyOf(items), page, size, total);
    }
}