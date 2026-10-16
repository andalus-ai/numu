package tech.kayys.numou.asset.application.timeline;

import java.time.Instant;
import java.util.Set;

/**
 * Filter + pagination criteria for a timeline query (ASSET-27 §acceptance).
 *
 * <p>{@code categories} of {@code null}/empty means "all categories". The window
 * is inclusive of {@code from} and exclusive of {@code to} when provided.</p>
 */
public record AssetTimelineFilter(
        Set<AssetTimelineCategory> categories,
        Instant from,
        Instant to,
        int page,
        int size
) {

    public AssetTimelineFilter {
        page = Math.max(page, 0);
        size = size <= 0 ? 50 : Math.min(size, 200);
    }

    public static AssetTimelineFilter all() {
        return new AssetTimelineFilter(null, null, null, 0, 50);
    }

    public boolean accepts(AssetTimelineEntry entry) {
        if (categories != null && !categories.isEmpty() && !categories.contains(entry.category())) {
            return false;
        }
        if (from != null && entry.occurredAt().isBefore(from)) {
            return false;
        }
        return to == null || entry.occurredAt().isBefore(to);
    }
}