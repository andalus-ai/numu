package tech.kayys.syirkah.accounting.domain.close;

import java.time.Instant;
import java.util.*;

/**
 * Root aggregate governing an accounting period close cycle.
 */
public final class CloseCycle {
    private final CloseCycleId id;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalPeriodId;
    private final List<CloseTask> tasks = new ArrayList<>();
    private CloseStatus status;
    private final Instant openedAt;
    private Instant closedAt;
    private String approvedBy;

    public CloseCycle(CloseCycleId id, String tenantId, String ledgerId, String fiscalPeriodId, List<CloseTask> initialTasks) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId must not be null");
        this.fiscalPeriodId = Objects.requireNonNull(fiscalPeriodId, "fiscalPeriodId must not be null");
        this.status = CloseStatus.OPEN;
        this.openedAt = Instant.now();
        if (initialTasks != null) {
            this.tasks.addAll(initialTasks);
        }
    }

    public static CloseCycle open(CloseCycleId id, String tenantId, String ledgerId, String fiscalPeriodId) {
        List<CloseTask> defaultTasks = List.of(
                new CloseTask("TASK_SUBLEDGER_CUTOFF", "Subledger Cut-off", CloseTaskKind.SUBLEDGER_CUTOFF, true),
                new CloseTask("TASK_ACCRUALS", "Post Accruals", CloseTaskKind.ACCRUALS, true),
                new CloseTask("TASK_DEPRECIATION", "Fixed Assets Depreciation", CloseTaskKind.DEPRECIATION, true),
                new CloseTask("TASK_FX_REVALUATION", "Multi-Currency FX Revaluation", CloseTaskKind.FX_REVALUATION, false),
                new CloseTask("TASK_RECONCILIATION", "Subledger & Bank Reconciliation", CloseTaskKind.RECONCILIATION, true),
                new CloseTask("TASK_LOCK", "General Ledger Hard Lock", CloseTaskKind.LOCK, true)
        );
        return new CloseCycle(id, tenantId, ledgerId, fiscalPeriodId, defaultTasks);
    }

    public void beginValidation() {
        if (status != CloseStatus.OPEN) {
            throw new IllegalStateException("CloseCycle must be OPEN to begin validation, currently: " + status);
        }
        this.status = CloseStatus.VALIDATING;
    }

    public void approve(String approver) {
        if (status != CloseStatus.VALIDATING) {
            throw new IllegalStateException("CloseCycle must be in VALIDATING state to approve, currently: " + status);
        }
        // Verify all mandatory tasks are completed
        boolean allMandatoryDone = tasks.stream()
                .filter(CloseTask::isMandatory)
                .allMatch(t -> t.status() == CloseTaskStatus.COMPLETED);
        if (!allMandatoryDone) {
            throw new IllegalStateException("Cannot approve close cycle: some mandatory tasks are incomplete");
        }
        this.approvedBy = Objects.requireNonNull(approver, "approver must not be null");
        this.status = CloseStatus.APPROVED;
    }

    public void lock() {
        if (status != CloseStatus.APPROVED) {
            throw new IllegalStateException("CloseCycle must be APPROVED before locking, currently: " + status);
        }
        this.status = CloseStatus.LOCKED;
        this.closedAt = Instant.now();
    }

    public Optional<CloseTask> findTask(String taskCode) {
        return tasks.stream().filter(t -> t.taskCode().equals(taskCode)).findFirst();
    }

    public CloseCycleId id() { return id; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String fiscalPeriodId() { return fiscalPeriodId; }
    public CloseStatus status() { return status; }
    public List<CloseTask> tasks() { return Collections.unmodifiableList(tasks); }
    public Instant openedAt() { return openedAt; }
    public Instant closedAt() { return closedAt; }
    public String approvedBy() { return approvedBy; }
}
