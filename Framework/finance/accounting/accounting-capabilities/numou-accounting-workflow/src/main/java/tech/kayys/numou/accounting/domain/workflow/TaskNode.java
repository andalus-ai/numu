package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.Set;

/** Human task node. */
public record TaskNode(NodeId id, String name, String assigneeRole, Set<NodeId> outgoing) implements WorkflowNode {
    public TaskNode {
        Objects.requireNonNull(id); Objects.requireNonNull(name);
        Objects.requireNonNull(assigneeRole); Objects.requireNonNull(outgoing);
        if (outgoing.isEmpty()) throw new IllegalArgumentException("TaskNode must have outgoing transitions");
        outgoing = Set.copyOf(outgoing);
    }
}
