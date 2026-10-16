package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.Set;

/** Entry point of a workflow process. */
public record StartNode(NodeId id, Set<NodeId> outgoing) implements WorkflowNode {
    public StartNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outgoing, "outgoing");
        if (outgoing.size() != 1) throw new IllegalArgumentException("StartNode must have exactly 1 successor");
        outgoing = Set.copyOf(outgoing);
    }
    public static StartNode of(String id, String target) {
        return new StartNode(NodeId.of(id), Set.of(NodeId.of(target)));
    }
}
