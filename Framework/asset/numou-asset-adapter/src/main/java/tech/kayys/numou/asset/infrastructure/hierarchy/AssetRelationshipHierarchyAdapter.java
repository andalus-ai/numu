package tech.kayys.numou.asset.infrastructure.hierarchy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.asset.application.hierarchy.AssetRelationshipHierarchy;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * BFS cycle detector over the relationship table (ASSET-17 §17.10).
 *
 * <p>Children of X are relationships where {@code relatedAssetId == X}
 * filtered to hierarchical types. A proposed {@code source → target} edge
 * creates a cycle iff {@code target} is reachable from {@code source}.
 * Depth is bounded by {@link #MAX_TRAVERSAL_DEPTH}.</p>
 */
@ApplicationScoped
public class AssetRelationshipHierarchyAdapter implements AssetRelationshipHierarchy {

    private final AssetRelationshipRepository relationships;

    @Inject
    public AssetRelationshipHierarchyAdapter(AssetRelationshipRepository relationships) {
        this.relationships = Objects.requireNonNull(relationships, "relationships");
    }

    @Override
    public CompletionStage<Boolean> wouldCreateCycle(
            String tenantId, UUID sourceAssetId, UUID targetAssetId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(sourceAssetId, "sourceAssetId cannot be null");
        Objects.requireNonNull(targetAssetId, "targetAssetId cannot be null");
        if (sourceAssetId.equals(targetAssetId)) {
            return CompletableFuture.completedFuture(true);
        }
        return bfs(tenantId, sourceAssetId, targetAssetId);
    }

    private CompletionStage<Boolean> bfs(String tenantId, UUID source, UUID target) {
        Set<UUID> visited = new HashSet<>();
        visited.add(source);
        Deque<Level> frontier = new ArrayDeque<>();
        frontier.add(new Level(source, 0));
        return step(tenantId, target, visited, frontier);
    }

    private CompletionStage<Boolean> step(
            String tenantId, UUID target, Set<UUID> visited, Deque<Level> frontier) {
        Level current;
        synchronized (frontier) {
            current = frontier.poll();
        }
        if (current == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (current.depth() >= MAX_TRAVERSAL_DEPTH) {
            return step(tenantId, target, visited, frontier);
        }
        return relationships.findByTarget(tenantId, AssetId.of(current.node()))
                .thenCompose(children -> {
                    for (AssetRelationship child : children) {
                        if (!child.type().hierarchical()) {
                            continue;
                        }
                        UUID childId = child.sourceAssetId().value();
                        if (childId.equals(target)) {
                            return CompletableFuture.completedFuture(true);
                        }
                        if (visited.add(childId)) {
                            synchronized (frontier) {
                                frontier.add(new Level(childId, current.depth() + 1));
                            }
                        }
                    }
                    return step(tenantId, target, visited, frontier);
                });
    }

    private record Level(UUID node, int depth) {
    }
}
