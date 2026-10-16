package tech.kayys.syirkah.accounting.domain.workflow;

import java.time.Instant;
import java.util.Objects;

/** Human task instance created by {@link TaskNode} or {@link ApprovalNode}. */
public final class Task {

    private final TaskId id;
    private final ProcessInstanceId processInstanceId;
    private final NodeId nodeId;
    private final String role;
    private String claimedBy;
    private TaskStatus status;
    private final Instant createdAt;
    private Instant completedAt;

    public Task(TaskId id, ProcessInstanceId processInstanceId, NodeId nodeId, String role) {
        this.id = Objects.requireNonNull(id);
        this.processInstanceId = Objects.requireNonNull(processInstanceId);
        this.nodeId = Objects.requireNonNull(nodeId);
        this.role = Objects.requireNonNull(role);
        this.status = TaskStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void claim(String user) {
        if (status != TaskStatus.OPEN) throw new IllegalStateException("Only OPEN tasks can be claimed");
        this.claimedBy = Objects.requireNonNull(user);
        this.status = TaskStatus.CLAIMED;
    }

    public void complete(String user) {
        if (status == TaskStatus.COMPLETED || status == TaskStatus.REJECTED)
            throw new IllegalStateException("Task already closed: " + status);
        this.claimedBy = user;
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void reject(String user) {
        if (status == TaskStatus.COMPLETED || status == TaskStatus.REJECTED)
            throw new IllegalStateException("Task already closed: " + status);
        this.claimedBy = user;
        this.status = TaskStatus.REJECTED;
        this.completedAt = Instant.now();
    }

    public TaskId id() { return id; }
    public ProcessInstanceId processInstanceId() { return processInstanceId; }
    public NodeId nodeId() { return nodeId; }
    public String role() { return role; }
    public String claimedBy() { return claimedBy; }
    public TaskStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
}
