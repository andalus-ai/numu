package tech.kayys.numou.asset.application.hierarchy;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Port for hierarchical cycle detection (ASSET-17 §17.9).
 *
 * <p>Determines whether adding {@code source COMPONENT_OF target} would close
 * a cycle, i.e. whether {@code target} is already a (transitive) descendant
 * of {@code source} through hierarchical relationships.</p>
 */
public interface AssetRelationshipHierarchy {

    /** Maximum BFS traversal depth for cycle detection. */
    int MAX_TRAVERSAL_DEPTH = 25;

    CompletionStage<Boolean> wouldCreateCycle(
            String tenantId,
            UUID sourceAssetId,
            UUID targetAssetId);
}
