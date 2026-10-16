package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.Set;

/** Terminal node of a workflow process. */
public record EndNode(NodeId id) implements WorkflowNode {
    public EndNode { Objects.requireNonNull(id, "id"); }
    @Override public Set<NodeId> outgoing() { return Set.of(); }
    public static EndNode of(String id) { return new EndNode(NodeId.of(id)); }
}
