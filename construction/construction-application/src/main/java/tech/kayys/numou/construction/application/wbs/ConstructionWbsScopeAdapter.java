package tech.kayys.numou.construction.application.wbs;

import tech.kayys.numou.construction.domain.wbs.WbsNode;
import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.domain.BreakdownTreeValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Anti-corruption adapter between the construction WBS model and the
 * industry-neutral scope validation contract.
 *
 * <p>The current construction model has one implicit WBS per project, so the
 * project ID is used as the neutral structure ID during validation. A future
 * persistence migration can replace this mapping with an explicit
 * {@code BreakdownStructureId} without leaking neutral scope types into the
 * construction domain model.</p>
 */
public final class ConstructionWbsScopeAdapter {
    private ConstructionWbsScopeAdapter() {
    }

    /**
     * Validates the existing project tree together with a candidate node.
     * The caller must persist the candidate only when the returned list is empty.
     */
    public static List<BreakdownTreeValidator.Violation> validateCandidateTree(
            Collection<WbsNode> existingNodes,
            WbsNode candidate) {
        Objects.requireNonNull(existingNodes, "Existing WBS nodes cannot be null");
        Objects.requireNonNull(candidate, "Candidate WBS node cannot be null");

        var candidateTree = new ArrayList<BreakdownNode>(existingNodes.size() + 1);
        existingNodes.forEach(node -> candidateTree.add(toBreakdownNode(node)));
        candidateTree.add(toBreakdownNode(candidate));
        return BreakdownTreeValidator.validate(candidateTree);
    }

    static BreakdownNode toBreakdownNode(WbsNode node) {
        Objects.requireNonNull(node, "WBS node cannot be null");
        return new BreakdownNode(
                new BreakdownNodeId(node.id().value()),
                new BreakdownStructureId(node.projectId()),
                node.parentNodeId() == null ? null : new BreakdownNodeId(node.parentNodeId()),
                node.code(),
                node.name(),
                node.type().name(),
                0);
    }
}
