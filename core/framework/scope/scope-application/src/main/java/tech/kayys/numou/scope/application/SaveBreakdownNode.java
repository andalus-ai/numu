package tech.kayys.numou.scope.application;

import tech.kayys.numou.scope.domain.BreakdownNode;
import tech.kayys.numou.scope.domain.BreakdownStructure;
import tech.kayys.numou.scope.domain.BreakdownStructureId;
import tech.kayys.numou.scope.domain.BreakdownStructureStatus;
import tech.kayys.numou.scope.domain.BreakdownTreeValidator;
import tech.kayys.numou.scope.spi.BreakdownNodeRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureRepository;
import tech.kayys.numou.scope.spi.BreakdownStructureWriteLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Validates a candidate node against its complete current structure before
 * delegating persistence. The lock must cover identity checks, validation,
 * and persistence so competing writers cannot validate the same stale tree.
 */
public final class SaveBreakdownNode {
    private final BreakdownStructureRepository structures;
    private final BreakdownNodeRepository nodes;
    private final BreakdownStructureWriteLock writeLock;

    public SaveBreakdownNode(
            BreakdownStructureRepository structures,
            BreakdownNodeRepository nodes,
            BreakdownStructureWriteLock writeLock) {
        this.structures = Objects.requireNonNull(structures, "Structure repository cannot be null");
        this.nodes = Objects.requireNonNull(nodes, "Node repository cannot be null");
        this.writeLock = Objects.requireNonNull(writeLock, "Structure write lock cannot be null");
    }

    public CompletionStage<BreakdownNode> execute(BreakdownNode candidate) {
        Objects.requireNonNull(candidate, "Candidate node cannot be null");
        return writeLock.withLock(candidate.structureId(), () -> executeValidated(candidate));
    }

    private CompletionStage<BreakdownNode> executeValidated(BreakdownNode candidate) {
        BreakdownStructureId structureId = candidate.structureId();
        return structures.findById(structureId).thenCompose(foundStructure -> {
            BreakdownStructure structure = foundStructure.orElseThrow(() ->
                    new IllegalArgumentException(
                            "Breakdown structure does not exist: " + structureId.value()));
            ensureWritable(structure);

            // Node IDs are global identities. An update may replace a node in
            // this structure, but must never silently move/overwrite an ID
            // already owned by another structure.
            return nodes.findById(candidate.id()).thenCompose(existingById -> {
                if (existingById.isPresent()
                        && !existingById.get().structureId().equals(structureId)) {
                    throw new IllegalArgumentException(
                            "Breakdown node already belongs to another structure: "
                                    + candidate.id().value());
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
        });
    }

    static void ensureWritable(BreakdownStructure structure) {
        if (structure.status() == BreakdownStructureStatus.RETIRED) {
            throw new IllegalStateException(
                    "A retired breakdown structure cannot be modified: " + structure.id().value());
        }
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
