package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.Set;

/** Automated command execution node. */
public record CommandNode(NodeId id, String commandKey, Set<NodeId> outgoing) implements WorkflowNode {
    public CommandNode {
        Objects.requireNonNull(id); Objects.requireNonNull(commandKey); Objects.requireNonNull(outgoing);
        if (outgoing.isEmpty()) throw new IllegalArgumentException("CommandNode outgoing must not be empty");
        outgoing = Set.copyOf(outgoing);
    }
}
