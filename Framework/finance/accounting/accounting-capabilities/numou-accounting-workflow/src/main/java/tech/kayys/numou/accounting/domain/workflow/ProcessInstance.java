package tech.kayys.syirkah.accounting.domain.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** State machine for a running workflow process instance. */
public final class ProcessInstance {

    private final ProcessInstanceId id;
    private final ProcessDefinitionId definitionId;
    private NodeId currentNodeId;
    private ProcessStatus status;
    private ProcessContext context;
    private final Instant startedAt;
    private Instant finishedAt;
    private final List<NodeId> executionHistory = new ArrayList<>();

    public ProcessInstance(ProcessInstanceId id, ProcessDefinitionId definitionId, NodeId startNodeId, ProcessContext context) {
        this.id = Objects.requireNonNull(id);
        this.definitionId = Objects.requireNonNull(definitionId);
        this.currentNodeId = Objects.requireNonNull(startNodeId);
        this.context = Objects.requireNonNull(context);
        this.status = ProcessStatus.RUNNING;
        this.startedAt = Instant.now();
        this.executionHistory.add(startNodeId);
    }

    public void advanceTo(NodeId nextNodeId) {
        this.currentNodeId = Objects.requireNonNull(nextNodeId);
        this.executionHistory.add(nextNodeId);
    }

    public void updateContext(ProcessContext newContext) {
        this.context = Objects.requireNonNull(newContext);
    }

    public void pauseForTask() {
        this.status = ProcessStatus.WAITING;
    }

    public void resume() {
        this.status = ProcessStatus.RUNNING;
    }

    public void complete() {
        this.status = ProcessStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail() {
        this.status = ProcessStatus.FAILED;
        this.finishedAt = Instant.now();
    }

    public ProcessInstanceId id() { return id; }
    public ProcessDefinitionId definitionId() { return definitionId; }
    public NodeId currentNodeId() { return currentNodeId; }
    public ProcessStatus status() { return status; }
    public ProcessContext context() { return context; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public List<NodeId> executionHistory() { return List.copyOf(executionHistory); }
}
