package tech.kayys.numou.workflow.engine;

import java.util.*;

/**
 * Deterministic in-memory scheduler for a workflow DAG.
 *
 * Nodes become ready when all of their predecessors have completed. Unknown
 * edges, duplicate node IDs, and cycles are rejected instead of silently
 * completing an invalid workflow.
 */
final class WorkflowDagScheduler {
    private final Map<String, Set<String>> successors = new LinkedHashMap<>();
    private final Map<String, Integer> remainingPredecessors = new LinkedHashMap<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private final PriorityQueue<String> ready = new PriorityQueue<>();
    private final Set<String> knownNodes = new LinkedHashSet<>();

    WorkflowDagScheduler(Map<String, Set<String>> adjacency, List<String> nodes) {
        Objects.requireNonNull(adjacency, "adjacency must not be null");
        Objects.requireNonNull(nodes, "nodes must not be null");
        for (String node : nodes) {
            if (node == null || node.isBlank()) {
                throw new IllegalArgumentException("Workflow node ID must not be blank");
            }
            if (!knownNodes.add(node)) {
                throw new IllegalArgumentException("Duplicate workflow node id: " + node);
            }
            successors.put(node, new LinkedHashSet<>());
            remainingPredecessors.put(node, 0);
        }
        for (String node : knownNodes) {
            Set<String> outgoing = adjacency.get(node);
            if (outgoing == null) {
                throw new IllegalArgumentException("Missing adjacency entry for workflow node: " + node);
            }
            for (String target : outgoing) {
                if (!knownNodes.contains(target)) {
                    throw new IllegalArgumentException("Workflow node " + node
                            + " references unknown next step: " + target);
                }
                if (node.equals(target)) {
                    throw new IllegalArgumentException("Workflow node cannot depend on itself: " + node);
                }
                if (successors.get(node).add(target)) {
                    remainingPredecessors.put(target, remainingPredecessors.get(target) + 1);
                }
            }
        }
        remainingPredecessors.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });
    }

    String nextReady() {
        return ready.peek();
    }

    void onNodeCompleted(String node) {
        if (!knownNodes.contains(node)) {
            throw new IllegalArgumentException("Unknown workflow node: " + node);
        }
        if (completed.contains(node)) {
            throw new IllegalStateException("Workflow node already completed: " + node);
        }
        if (!ready.remove(node)) {
            throw new IllegalStateException("Workflow node is not ready: " + node);
        }
        completed.add(node);
        for (String successor : successors.get(node)) {
            int remaining = remainingPredecessors.compute(successor, (key, count) -> count - 1);
            if (remaining == 0) {
                ready.add(successor);
            }
        }
    }
}
