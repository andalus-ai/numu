package tech.kayys.numou.accounting.domain.workflow;

import java.util.Set;

/** Base contract for every node in a {@link ProcessDefinition}. */
public interface WorkflowNode {
    NodeId id();
    Set<NodeId> outgoing();
}
