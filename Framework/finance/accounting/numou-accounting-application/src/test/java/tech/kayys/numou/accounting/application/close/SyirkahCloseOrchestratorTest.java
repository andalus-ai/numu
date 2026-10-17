package tech.kayys.numou.accounting.application.close;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.close.CloseCycle;
import tech.kayys.numou.accounting.domain.close.CloseCycleId;
import tech.kayys.numou.accounting.domain.close.CloseTaskStatus;
import tech.kayys.numou.accounting.domain.close.SyirkahCloseTaskCodes;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahCloseOrchestratorTest {
    private FinancialCloseService closeService;
    private FakeOperations operations;
    private SyirkahCloseOrchestrator orchestrator;
    private CloseCycleId cycleId;

    @BeforeEach
    void setUp() {
        closeService = new FinancialCloseService(new CloseValidationEngine(), new PeriodLockEngine());
        cycleId = CloseCycleId.of("SYIRKAH-2026-01");
        closeService.openCycle(cycleId, "tenant-1", "ledger-1", "2026-01", true);
        operations = new FakeOperations();
        orchestrator = new SyirkahCloseOrchestrator(closeService, operations);
    }

    @Test
    void stagesMustPersistAndCompleteInOrder() {
        assertThrows(IllegalStateException.class, () -> orchestrator.allocateResult(cycleId, "maker"));
        assertEquals("result-snapshot", orchestrator.calculateResult(cycleId, "maker"));
        assertEquals(CloseTaskStatus.COMPLETED, task(SyirkahCloseTaskCodes.RESULT).status());
        assertEquals("allocation-snapshot", orchestrator.allocateResult(cycleId, "maker"));
        assertEquals(CloseTaskStatus.COMPLETED, task(SyirkahCloseTaskCodes.ALLOCATION).status());
        assertEquals("journal-123", orchestrator.approveAndPostDistribution(cycleId, "checker"));
        assertEquals(CloseTaskStatus.COMPLETED, task(SyirkahCloseTaskCodes.DISTRIBUTION).status());
        assertTrue(operations.posted);
    }

    @Test
    void distributionTaskIsNotCompletedWhenPostingFails() {
        orchestrator.calculateResult(cycleId, "maker");
        orchestrator.allocateResult(cycleId, "maker");
        operations.failPosting = true;
        assertThrows(IllegalStateException.class,
                () -> orchestrator.approveAndPostDistribution(cycleId, "checker"));
        assertEquals(CloseTaskStatus.PENDING, task(SyirkahCloseTaskCodes.DISTRIBUTION).status());
    }

    @Test
    void genericCloseApiCannotBypassSyirkahWorkflow() {
        assertThrows(IllegalStateException.class,
                () -> closeService.completeTask(cycleId, SyirkahCloseTaskCodes.RESULT, "operator"));
    }

    private tech.kayys.numou.accounting.domain.close.CloseTask task(String code) {
        return closeService.findById(cycleId).orElseThrow().findTask(code).orElseThrow();
    }

    private static final class FakeOperations implements SyirkahCloseOperations {
        private boolean resultPersisted;
        private boolean allocationPersisted;
        private boolean posted;
        private boolean failPosting;

        @Override
        public String calculateAndPersistResult(CloseCycleId cycleId, String actor) {
            resultPersisted = true;
            return "result-snapshot";
        }

        @Override
        public String allocateAndPersistResult(CloseCycleId cycleId, String actor) {
            if (!resultPersisted) throw new IllegalStateException("result missing");
            allocationPersisted = true;
            return "allocation-snapshot";
        }

        @Override
        public String approveAndPostDistribution(CloseCycleId cycleId, String actor) {
            if (!allocationPersisted) throw new IllegalStateException("allocation missing");
            if (failPosting) throw new IllegalStateException("journal posting failed");
            posted = true;
            return "journal-123";
        }
    }
}
