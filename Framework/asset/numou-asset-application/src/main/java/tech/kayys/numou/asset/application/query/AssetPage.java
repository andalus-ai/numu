package tech.kayys.numou.asset.application.query;

import java.util.List;

/** Simple page envelope for asset queries (see ASSET-14). */
public record AssetPage<T>(List<T> items, int page, int size, long total) {

    public static <T> AssetPage<T> of(List<T> items, int page, int size, long total) {
        return new AssetPage<>(List.copyOf(items), page, size, total);
    }
}
