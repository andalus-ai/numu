package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.relationship.AssetRelationship;
import tech.kayys.numou.asset.domain.repository.AssetRelationshipRepository;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;

import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Hierarchy read handlers built over the relationship table only
 * (ASSET-17 §17.13-17.18). Depth is explicit and bounded:
 * default {@code 3}, maximum {@code 10}; violations raise
 * {@code asset.hierarchy.depth-exceeded}.
 */
@jakarta.enterprise.context.ApplicationScoped
public class GetAssetHierarchyHandler {

    public static final int DEFAULT_DEPTH = 3;

    public static final int MAX_DEPTH = 10;

    private final AssetRelationshipRepository relationships;

    public GetAssetHierarchyHandler(AssetRelationshipRepository relationships) {
        this.relationships = Objects.requireNonNull(relationships, "relationships");
    }

    public Uni<Optional<UUID>> handle(GetAssetParentQuery query) {
        return Uni.createFrom()
                .completionStage(() -> relationships.findBySource(
                        query.tenantId(), query.assetId()))
                .map(rels -> rels.stream()
                        .filter(r -> r.type().hierarchical())
                        .map(r -> r.relatedAssetId().value())
                        .findFirst());
    }

    public Uni<List<UUID>> handle(GetAssetComponentsQuery query) {
        return Uni.createFrom()
                .completionStage(() -> relationships.findByTarget(
                        query.tenantId(), query.assetId()))
                .map(rels -> rels.stream()
                        .filter(r -> r.type().hierarchical())
                        .map(r -> r.sourceAssetId().value())
                        .sorted()
                        .toList());
    }

    public Uni<AssetHierarchy> handle(GetAssetHierarchyQuery query) {
        int depth = query.depth() <= 0 ? DEFAULT_DEPTH : query.depth();
        if (depth > MAX_DEPTH) {
            return Uni.createFrom().failure(new ApplicationErrorException(
                    ApplicationError.of("asset.hierarchy.depth-exceeded",
                            "Requested depth " + depth + " exceeds maximum " + MAX_DEPTH)));
        }
        int effective = depth;
        return buildChildren(query.tenantId(), query.assetId().value(), effective)
                .map(children -> new AssetHierarchy(query.assetId().value(), children));
    }

    private Uni<List<AssetHierarchyNode>> buildChildren(String tenantId, UUID parent, int remaining) {
        if (remaining <= 0) {
            return Uni.createFrom().item(List.of());
        }
        return Uni.createFrom()
                .completionStage(() -> relationships.findByTarget(tenantId, AssetId.of(parent)))
                .flatMap(rels -> {
                    List<AssetRelationship> hierarchical = rels.stream()
                            .filter(r -> r.type().hierarchical())
                            .sorted((a, b) -> a.sourceAssetId().value()
                                    .compareTo(b.sourceAssetId().value()))
                            .toList();
                    if (hierarchical.isEmpty()) {
                        return Uni.createFrom().item(List.<AssetHierarchyNode>of());
                    }
                    List<Uni<AssetHierarchyNode>> branches = new ArrayList<>();
                    for (AssetRelationship rel : hierarchical) {
                        UUID child = rel.sourceAssetId().value();
                        branches.add(buildChildren(tenantId, child, remaining - 1)
                                .map(kids -> new AssetHierarchyNode(child, kids)));
                    }
                    return Uni.join().all(branches).andCollectFailures();
                });
    }
}
