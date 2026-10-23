package tech.kayys.numou.planning.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, validated work-breakdown tree. It intentionally stores no
 * construction-specific fields and does not assume that node weights or
 * quantity units are comparable.
 */
public final class WorkBreakdownStructure {
    private final UUID id;
    private final String code;
    private final List<WorkBreakdownNode> nodes;
    private final Map<UUID, WorkBreakdownNode> byId;
    private final Map<UUID, List<WorkBreakdownNode>> children;

    private WorkBreakdownStructure(UUID id, String code, List<WorkBreakdownNode> nodes) {
        this.id = Objects.requireNonNull(id, "Structure id cannot be null");
        this.code = requireText(code, "Structure code");
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "Nodes cannot be null"));
        if (this.nodes.isEmpty()) throw new IllegalArgumentException("A work breakdown structure cannot be empty");

        Map<UUID, WorkBreakdownNode> index = new HashMap<>();
        Map<UUID, List<WorkBreakdownNode>> childIndex = new HashMap<>();
        Set<String> siblingCodes = new HashSet<>();
        Set<String> siblingPositions = new HashSet<>();
        int roots = 0;

        for (WorkBreakdownNode node : this.nodes) {
            Objects.requireNonNull(node, "Node cannot be null");
            if (index.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Node id must be unique within a structure");
            }
            String parentKey = node.parentId() == null ? "<ROOT>" : node.parentId().toString();
            if (!siblingCodes.add(parentKey + "|" + node.code().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Node code must be unique among siblings");
            }
            if (!siblingPositions.add(parentKey + "|" + node.position())) {
                throw new IllegalArgumentException("Node position must be unique among siblings");
            }
            if (node.parentId() == null) roots++;
            else childIndex.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node);
        }
        if (roots != 1) throw new IllegalArgumentException("A work breakdown structure must have exactly one root");
        for (WorkBreakdownNode node : this.nodes) {
            if (node.parentId() != null && !index.containsKey(node.parentId())) {
                throw new IllegalArgumentException("Node references a missing parent");
            }
        }

        for (WorkBreakdownNode node : this.nodes) {
            Set<UUID> path = new HashSet<>();
            WorkBreakdownNode current = node;
            while (current != null) {
                if (!path.add(current.id())) throw new IllegalArgumentException("Work breakdown structure contains a cycle");
                current = current.parentId() == null ? null : index.get(current.parentId());
            }
        }
        childIndex.values().forEach(list -> list.sort(java.util.Comparator.comparingInt(WorkBreakdownNode::position)));
        this.byId = Map.copyOf(index);
        Map<UUID, List<WorkBreakdownNode>> immutableChildren = new HashMap<>();
        childIndex.forEach((key, value) -> immutableChildren.put(key, List.copyOf(value)));
        this.children = Map.copyOf(immutableChildren);
    }

    public static WorkBreakdownStructure create(UUID id, String code, List<WorkBreakdownNode> nodes) {
        return new WorkBreakdownStructure(id, code, nodes);
    }

    public WorkBreakdownNode node(UUID nodeId) {
        WorkBreakdownNode node = byId.get(Objects.requireNonNull(nodeId, "Node id cannot be null"));
        if (node == null) throw new IllegalArgumentException("Node does not belong to this structure");
        return node;
    }

    public List<WorkBreakdownNode> childrenOf(UUID parentId) {
        node(parentId);
        return children.getOrDefault(parentId, List.of());
    }

    /** Returns descendants in deterministic depth-first, sibling-position order. */
    public List<WorkBreakdownNode> descendantsOf(UUID parentId) {
        node(parentId);
        List<WorkBreakdownNode> result = new ArrayList<>();
        collectDescendants(parentId, result);
        return List.copyOf(result);
    }

    private void collectDescendants(UUID parentId, List<WorkBreakdownNode> result) {
        for (WorkBreakdownNode child : children.getOrDefault(parentId, List.of())) {
            result.add(child);
            collectDescendants(child.id(), result);
        }
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public List<WorkBreakdownNode> nodes() { return nodes; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
