package tech.kayys.numou.asset.application.inspection;

import java.util.List;

/** Page envelope for inspection queries. */
public record AssetInspectionPage<T>(List<T> items, int page, int size, long total) {

    public static <T> AssetInspectionPage<T> of(List<T> items, int page, int size, long total) {
        return new AssetInspectionPage<>(List.copyOf(items), page, size, total);
    }
}
