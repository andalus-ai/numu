package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;
import java.util.Objects;

/**
 * An orchestrated operational step within a period close cycle.
 */
public final class CloseTask {
    private final String taskCode;
    private final String name;
    private final CloseTaskKind kind;
    private final boolean mandatory;
    private CloseTaskStatus status;
    private String completedBy;
    private Instant completedAt;

    public CloseTask(String taskCode, String name, CloseTaskKind kind, boolean mandatory) {
        this.taskCode = Objects.requireNonNull(taskCode, "taskCode must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.mandatory = mandatory;
        this.status = CloseTaskStatus.PENDING;
    }

    public void start() {
        if (status == CloseTaskStatus.COMPLETED) {
            throw new IllegalStateException("Task is already completed: " + taskCode);
        }
        this.status = CloseTaskStatus.IN_PROGRESS;
    }

    public void complete(String user) {
        this.completedBy = Objects.requireNonNull(user, "completedBy user must not be null");
        this.completedAt = Instant.now();
        this.status = CloseTaskStatus.COMPLETED;
    }

    public void block() {
        this.status = CloseTaskStatus.BLOCKED;
    }

    public String taskCode() { return taskCode; }
    public String name() { return name; }
    public CloseTaskKind kind() { return kind; }
    public boolean isMandatory() { return mandatory; }
    public CloseTaskStatus status() { return status; }
    public String completedBy() { return completedBy; }
    public Instant completedAt() { return completedAt; }
}
