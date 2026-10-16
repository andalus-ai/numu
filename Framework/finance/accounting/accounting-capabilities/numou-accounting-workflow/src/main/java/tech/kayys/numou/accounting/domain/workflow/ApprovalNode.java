package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Multi-approver approval node. */
public record ApprovalNode(NodeId id, String name, List<String> approvers, ApprovalMode mode, Set<NodeId> outgoing) implements WorkflowNode {
    public ApprovalNode {
        Objects.requireNonNull(id); Objects.requireNonNull(name);
        Objects.requireNonNull(approvers); Objects.requireNonNull(mode); Objects.requireNonNull(outgoing);
        if (approvers.isEmpty()) throw new IllegalArgumentException("approvers must not be empty");
        if (outgoing.isEmpty()) throw new IllegalArgumentException("outgoing must not be empty");
        approvers = List.copyOf(approvers);
        outgoing = Set.copyOf(outgoing);
    }
}
