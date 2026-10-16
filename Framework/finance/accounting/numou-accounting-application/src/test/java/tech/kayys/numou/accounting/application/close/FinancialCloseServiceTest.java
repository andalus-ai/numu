package tech.kayys.syirkah.accounting.application.close;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseCycleId;
import tech.kayys.syirkah.accounting.domain.close.CloseStatus;

import static org.junit.jupiter.api.Assertions.*;

class FinancialCloseServiceTest {

    private FinancialCloseService closeService;
    private PeriodLockEngine lockEngine;

    @BeforeEach
    void setUp() {
        CloseValidationEngine validationEngine = new CloseValidationEngine();
        lockEngine = new PeriodLockEngine();
        closeService = new FinancialCloseService(validationEngine, lockEngine);
    }

    @Test
    void testFullFinancialCloseLifecycle() {
        CloseCycleId id = CloseCycleId.of("CC-2026-Q1");
        CloseCycle cycle = closeService.openCycle(id, "T01", "L01", "2026-Q1");
        assertEquals(CloseStatus.OPEN, cycle.status());
        assertFalse(lockEngine.isPeriodLocked("T01", "L01", "2026-Q1"));

        // Complete all mandatory tasks
        closeService.completeTask(id, "TASK_SUBLEDGER_CUTOFF", "auditor1");
        closeService.completeTask(id, "TASK_ACCRUALS", "accountant1");
        closeService.completeTask(id, "TASK_DEPRECIATION", "accountant1");
        closeService.completeTask(id, "TASK_RECONCILIATION", "controller1");
        closeService.completeTask(id, "TASK_LOCK", "controller1");

        // Begin validation
        var valResult = closeService.beginValidation(id);
        assertTrue(valResult.valid());
        assertTrue(valResult.errors().isEmpty());

        // Approve and apply hard lock
        CloseCycle lockedCycle = closeService.approveAndLock(id, "CFO_USER");
        assertEquals(CloseStatus.LOCKED, lockedCycle.status());
        assertTrue(lockEngine.isPeriodLocked("T01", "L01", "2026-Q1"));

        // Assert posting is blocked
        assertThrows(IllegalStateException.class, () -> lockEngine.assertNotLocked("T01", "L01", "2026-Q1"));
    }

    @Test
    void testApprovalFailsWhenMandatoryTasksIncomplete() {
        CloseCycleId id = CloseCycleId.of("CC-2026-Q2");
        closeService.openCycle(id, "T01", "L01", "2026-Q2");
        closeService.beginValidation(id);

        assertThrows(IllegalStateException.class, () -> closeService.approveAndLock(id, "CFO_USER"));
    }
}
