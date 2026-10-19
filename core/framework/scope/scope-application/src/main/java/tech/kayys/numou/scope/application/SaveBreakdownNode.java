package tech.kayys.numou.scope.application;

import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownNodeId;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.domain.BreakdownTreeValidator;
import tech.kayys.numou.scope.spi.BreakdownNodeRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Validates a candidate node against its complete current structure before
 * delegating persistence. Adapters must still provide transaction/concurrency
 * protection when multiple writers can change the same tree concurrently.
 */
public final class SaveBreakdownNode {
    private final BreakdownStructureRepository structures;
    private final BreakdownNodeRepository nodes;

    public SaveBreakdownNode(
            BreakdownStructureRepository structures,
            BreakdownNodeRepository nodes) {
        this.structures = Objects.requireNonNull(structures, "Structure repository cannot be null");
        this.nodes = Objects.requireNonNull(nodes, "Node repository cannot be null");
    }

    public CompletionStage<BreakdownNode> execute(BreakdownNode candidate) {
        Objects.requireNonNull(candidate, "Candidate node cannot be null");

        BreakdownStructureId structureId = candidate.structureId();
        return structures.findById(structureId).thenCompose(structure -> {
            if (structure.isEmpty()) {
                throw new IllegalArgumentException(
                        "Breakdown structure does not exist: " + structureId.value());
            }

            return nodes.findByStructureId(structureId).thenCompose(existing -> {
                List<BreakdownNode> candidateTree = new ArrayList<>(existing.size() + 1);
                boolean replaced = false;
                for (BreakdownNode node : existing) {
                    if (!node.structureId().equals(structureId)) {
                        throw new IllegalStateException(
                                "Repository returned a node outside the requested structure");
                    }
                    if (node.id().equals(candidate.id())) {
                        candidateTree.add(candidate);
                        replaced = true;
                    } else {
                        candidateTree.add(node);
                    }
                }
                if (!replaced) {
                    candidateTree.add(candidate);
                }

                List<BreakdownTreeValidator.Violation> violations =
                        BreakdownTreeValidator.validate(candidateTree);
                if (!violations.isEmpty()) {
                    throw new InvalidBreakdownTreeException(violations);
                }
                return nodes.save(candidate);
            });
        });
    }

    public static final class InvalidBreakdownTreeException extends IllegalArgumentException {
        private final List<BreakdownTreeValidator.Violation> violations;

        public InvalidBreakdownTreeException(List<BreakdownTreeValidator.Violation> violations) {
            super(formatMessage(violations));
            this.violations = List.copyOf(Objects.requireNonNull(violations, "Violations cannot be null"));
            if (this.violations.isEmpty()) {
                throw new IllegalArgumentException("At least one tree violation is required");
            }
        }

        public List<BreakdownTreeValidator.Violation> violations() {
            return violations;
        }

        private static String formatMessage(List<BreakdownTreeValidator.Violation> violations) {
            Objects.requireNonNull(violations, "Violations cannot be null");
            return "Breakdown tree is invalid: " + violations.stream()
                    .map(v -> v.code() + " for node " + v.nodeId().value() + ": " + v.message())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("unknown violation");
        }
    }
}
