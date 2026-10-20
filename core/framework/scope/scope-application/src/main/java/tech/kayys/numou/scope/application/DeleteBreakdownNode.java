package tech.kayys.numou.scope.application;

import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructure;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.spi.BreakdownNodeRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Deletes a leaf from a breakdown structure.
 *
 * <p>The same structure-scoped lock used by node saves must cover the lookup,
 * child check and delete. This prevents a concurrent child creation from
 * racing a parent deletion when all structural writers use the same lock.</p>
 */
public final class DeleteBreakdownNode {
    private final BreakdownStructureRepository structures;
    private final BreakdownNodeRepository nodes;
    private final BreakdownStructureWriteLock writeLock;

    public DeleteBreakdownNode(
            BreakdownStructureRepository structures,
            BreakdownNodeRepository nodes,
            BreakdownStructureWriteLock writeLock) {
        this.structures = Objects.requireNonNull(structures, "Structure repository cannot be null");
        this.nodes = Objects.requireNonNull(nodes, "Node repository cannot be null");
        this.writeLock = Objects.requireNonNull(writeLock, "Structure write lock cannot be null");
    }

    public CompletionStage<Void> execute(
            BreakdownStructureId structureId,
            BreakdownNodeId nodeId) {
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "Node id cannot be null");
        return writeLock.withLock(structureId, () -> deleteLocked(structureId, nodeId));
    }

    private CompletionStage<Void> deleteLocked(
            BreakdownStructureId structureId,
            BreakdownNodeId nodeId) {
        return structures.findById(structureId).thenCompose(found -> {
            BreakdownStructure structure = found.orElseThrow(() ->
                    new IllegalArgumentException(
                            "Breakdown structure does not exist: " + structureId.value()));
            SaveBreakdownNode.ensureWritable(structure);

            return nodes.findById(nodeId).thenCompose(foundNode -> {
                var node = foundNode.orElseThrow(() ->
                        new IllegalArgumentException("Breakdown node does not exist: " + nodeId.value()));
                if (!node.structureId().equals(structureId)) {
                    throw new IllegalArgumentException(
                            "Breakdown node does not belong to structure: " + structureId.value());
                }
                return nodes.findByParentId(structureId, nodeId).thenCompose(children -> {
                    if (!children.isEmpty()) {
                        throw new IllegalStateException(
                                "A breakdown node with children cannot be deleted: " + nodeId.value());
                    }
                    return nodes.delete(nodeId);
                });
            });
        });
    }
}
