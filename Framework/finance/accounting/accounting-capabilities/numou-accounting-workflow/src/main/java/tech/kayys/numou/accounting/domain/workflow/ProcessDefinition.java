package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Workflow process definition blueprint. */
public final class ProcessDefinition {

    private final ProcessDefinitionId id;
    private final String name;
    private final Map<NodeId, WorkflowNode> nodes;
    private final NodeId startNodeId;

    public ProcessDefinition(ProcessDefinitionId id, String name, Map<NodeId, WorkflowNode> nodes, NodeId startNodeId) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.nodes = new HashMap<>(Objects.requireNonNull(nodes));
        this.startNodeId = Objects.requireNonNull(startNodeId);
        if (!this.nodes.containsKey(startNodeId)) throw new IllegalArgumentException("Start node not present in nodes map");
        if (!(this.nodes.get(startNodeId) instanceof StartNode)) throw new IllegalArgumentException("Configured startNode is not a StartNode instance");
    }

    public ProcessDefinitionId id() { return id; }
    public String name() { return name; }
    public NodeId startNodeId() { return startNodeId; }
    public Map<NodeId, WorkflowNode> nodes() { return Collections.unmodifiableMap(nodes); }
    public WorkflowNode getNode(NodeId nodeId) { return nodes.get(nodeId); }
}
