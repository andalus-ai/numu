package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.Set;

/** Conditional branching node. */
public record DecisionNode(NodeId id, String expression, NodeId trueBranch, NodeId falseBranch) implements WorkflowNode {
    public DecisionNode {
        Objects.requireNonNull(id); Objects.requireNonNull(expression);
        Objects.requireNonNull(trueBranch); Objects.requireNonNull(falseBranch);
    }
    @Override public Set<NodeId> outgoing() { return Set.of(trueBranch, falseBranch); }
}
