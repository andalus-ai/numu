package tech.kayys.numou.accounting.application.close;

import tech.kayys.numou.accounting.domain.close.CloseCycle;
import tech.kayys.numou.accounting.domain.close.CloseCycleId;
import tech.kayys.numou.accounting.domain.close.CloseStatus;
import tech.kayys.numou.accounting.domain.close.SyirkahCloseTaskCodes;

import java.util.Objects;

/**
 * Coordinates the Syirkah close state machine.
 *
 * Each operation must persist its own immutable snapshot / posting result before
 * this orchestrator completes the matching close task. Journal posting is kept
 * behind an application port so this class cannot manufacture a posted status.
 */
public final class SyirkahCloseOrchestrator {
    private final FinancialCloseService closeService;
    private final SyirkahCloseOperations operations;

    public SyirkahCloseOrchestrator(FinancialCloseService closeService,
                                    SyirkahCloseOperations operations) {
        this.closeService = Objects.requireNonNull(closeService, "closeService");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public String calculateResult(CloseCycleId cycleId, String actor) {
        requireOpenSyirkahCycle(cycleId);
        requireTaskNotCompleted(cycleId, SyirkahCloseTaskCodes.RESULT);
        String snapshot = requireReference(operations.calculateAndPersistResult(cycleId, actor), "result snapshot");
        closeService.completeSyirkahTask(cycleId, SyirkahCloseTaskCodes.RESULT, actor, snapshot);
        return snapshot;
    }

    public String allocateResult(CloseCycleId cycleId, String actor) {
        requireTaskCompleted(cycleId, SyirkahCloseTaskCodes.RESULT);
        requireTaskNotCompleted(cycleId, SyirkahCloseTaskCodes.ALLOCATION);
        String allocation = requireReference(operations.allocateAndPersistResult(cycleId, actor), "allocation snapshot");
        closeService.completeSyirkahTask(cycleId, SyirkahCloseTaskCodes.ALLOCATION, actor, allocation);
        return allocation;
    }

    public String approveAndPostDistribution(CloseCycleId cycleId, String actor) {
        requireTaskCompleted(cycleId, SyirkahCloseTaskCodes.RESULT);
        requireTaskCompleted(cycleId, SyirkahCloseTaskCodes.ALLOCATION);
        requireTaskNotCompleted(cycleId, SyirkahCloseTaskCodes.DISTRIBUTION);
        String journal = requireReference(operations.approveAndPostDistribution(cycleId, actor), "posted journal reference");
        closeService.completeSyirkahTask(cycleId, SyirkahCloseTaskCodes.DISTRIBUTION, actor, journal);
        return journal;
    }

    private void requireOpenSyirkahCycle(CloseCycleId id) {
        CloseCycle cycle = closeService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CloseCycle not found: " + id.value()));
        if (cycle.status() != CloseStatus.OPEN) {
            throw new IllegalStateException("Syirkah close operations require an OPEN close cycle");
        }
        requireTaskExists(cycle, SyirkahCloseTaskCodes.RESULT);
        requireTaskExists(cycle, SyirkahCloseTaskCodes.ALLOCATION);
        requireTaskExists(cycle, SyirkahCloseTaskCodes.DISTRIBUTION);
    }

    private void requireTaskCompleted(CloseCycleId id, String taskCode) {
        CloseCycle cycle = closeService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CloseCycle not found: " + id.value()));
        if (cycle.status() != CloseStatus.OPEN) {
            throw new IllegalStateException("Syirkah close operations require an OPEN close cycle");
        }
        var task = cycle.findTask(taskCode)
                .orElseThrow(() -> new IllegalStateException("Syirkah-enabled close task missing: " + taskCode));
        if (task.status() != tech.kayys.numou.accounting.domain.close.CloseTaskStatus.COMPLETED) {
            throw new IllegalStateException("Prerequisite close task is incomplete: " + taskCode);
        }
    }

    private void requireTaskNotCompleted(CloseCycleId id, String taskCode) {
        CloseCycle cycle = closeService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CloseCycle not found: " + id.value()));
        var task = cycle.findTask(taskCode)
                .orElseThrow(() -> new IllegalStateException("Syirkah-enabled close task missing: " + taskCode));
        if (task.status() == tech.kayys.numou.accounting.domain.close.CloseTaskStatus.COMPLETED) {
            throw new IllegalStateException("Syirkah close task is already completed: " + taskCode);
        }
    }

    private static void requireTaskExists(CloseCycle cycle, String taskCode) {
        if (cycle.findTask(taskCode).isEmpty()) {
            throw new IllegalStateException("This close cycle is not configured for Syirkah: " + taskCode);
        }
    }

    private static String requireReference(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Syirkah operation did not return a persisted " + description);
        }
        return value;
    }
}
