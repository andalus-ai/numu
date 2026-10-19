package tech.kayys.numou.asset.application.query;

import java.util.List;
import java.util.UUID;

/**
 * Root read model of an asset component hierarchy (ASSET-17 §17.15).
 */
public record AssetHierarchy(UUID rootAssetId, List<AssetHierarchyNode> children) {

    public AssetHierarchy {
        if (children == null) {
            throw new IllegalArgumentException("children cannot be null");
        }
        children = List.copyOf(children);
    }
}
