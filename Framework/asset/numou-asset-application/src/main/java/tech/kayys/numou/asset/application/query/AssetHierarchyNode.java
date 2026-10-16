package tech.kayys.numou.asset.application.query;

import java.util.List;
import java.util.UUID;

/**
 * Read-model node of the asset component hierarchy (ASSET-17 §17.15).
 *
 * <p>Built from the relationship table only — never by recursively loading
 * Asset aggregates.</p>
 */
public record AssetHierarchyNode(UUID assetId, List<AssetHierarchyNode> children) {

    public AssetHierarchyNode {
        if (children == null) {
            throw new IllegalArgumentException("children cannot be null");
        }
        children = List.copyOf(children);
    }
}
