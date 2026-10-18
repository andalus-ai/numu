package tech.kayys.numou.scope.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates invariants that cannot be checked by an individual immutable node.
 *
 * <p>Call this at the application/repository boundary with the complete set of
 * nodes for a structure before persisting a structural change. Codes are
 * compared case-insensitively among siblings.</p>
 */
public final class BreakdownTreeValidator {
    private BreakdownTreeValidator() {
    }

    public static List<Violation> validate(Collection<BreakdownNode> nodes) {
        Objects.requireNonNull(nodes, "Breakdown nodes cannot be null");
        var violations = new ArrayList<Violation>();
        var byId = new HashMap<BreakdownNodeId, BreakdownNode>();

        for (var node : nodes) {
            Objects.requireNonNull(node, "Breakdown node collection cannot contain null");
            var previous = byId.putIfAbsent(node.id(), node);
            if (previous != null) {
                violations.add(new Violation(Code.DUPLICATE_NODE_ID, node.id(),
                        "Node id occurs more than once in the supplied tree"));
            }
        }

        var siblingCodes = new HashSet<String>();
        for (var node : byId.values()) {
            if (node.parentId() != null) {
                var parent = byId.get(node.parentId());
                if (parent == null) {
                    violations.add(new Violation(Code.MISSING_PARENT, node.id(),
                            "Parent node does not exist in the supplied tree"));
                } else if (!parent.structureId().equals(node.structureId())) {
                    violations.add(new Violation(Code.PARENT_IN_DIFFERENT_STRUCTURE, node.id(),
                            "Parent and child must belong to the same breakdown structure"));
                }
            }

            String parentKey = node.parentId() == null ? "<root>" : node.parentId().value().toString();
            String codeKey = node.structureId().value() + "|" + parentKey + "|"
                    + node.code().toLowerCase(Locale.ROOT);
            if (!siblingCodes.add(codeKey)) {
                violations.add(new Violation(Code.DUPLICATE_SIBLING_CODE, node.id(),
                        "Sibling node codes must be unique, ignoring case"));
            }
        }

        for (var node : byId.values()) {
            detectCycle(node, byId, violations);
        }
        return List.copyOf(violations);
    }

    private static void detectCycle(
            BreakdownNode start,
            Map<BreakdownNodeId, BreakdownNode> byId,
            List<Violation> violations) {
        var path = new HashSet<BreakdownNodeId>();
        var current = start;
        while (current != null && current.parentId() != null) {
            if (!path.add(current.id())) {
                violations.add(new Violation(Code.CYCLE, start.id(),
                        "Parent references form a cycle"));
                return;
            }
            current = byId.get(current.parentId());
        }
        if (current != null && !path.add(current.id())) {
            violations.add(new Violation(Code.CYCLE, start.id(),
                    "Parent references form a cycle"));
        }
    }

    public enum Code {
        DUPLICATE_NODE_ID,
        MISSING_PARENT,
        PARENT_IN_DIFFERENT_STRUCTURE,
        DUPLICATE_SIBLING_CODE,
        CYCLE
    }

    public record Violation(Code code, BreakdownNodeId nodeId, String message) {
        public Violation {
            Objects.requireNonNull(code, "Violation code cannot be null");
            Objects.requireNonNull(nodeId, "Violation node id cannot be null");
            Objects.requireNonNull(message, "Violation message cannot be null");
        }
    }
}
