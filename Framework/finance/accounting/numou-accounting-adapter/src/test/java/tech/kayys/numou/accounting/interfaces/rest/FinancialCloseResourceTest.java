package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.close.CloseValidationEngine;
import tech.kayys.syirkah.accounting.application.close.FinancialCloseService;
import tech.kayys.syirkah.accounting.application.close.PeriodLockEngine;
import tech.kayys.syirkah.accounting.domain.close.CloseCycle;
import tech.kayys.syirkah.accounting.domain.close.CloseStatus;

import static org.junit.jupiter.api.Assertions.*;

class FinancialCloseResourceTest {

    private FinancialCloseResource resource;
    private FinancialCloseService service;

    @BeforeEach
    void setUp() {
        service = new FinancialCloseService(new CloseValidationEngine(), new PeriodLockEngine());
        resource = new FinancialCloseResource();
        resource.closeService = service;
    }

    @Test
    void testOpenAndCompleteCycle() {
        var openReq = new FinancialCloseResource.OpenCycleRequest("CC-REST-01", "T01", "L01", "2026-03");
        Response openResp = resource.openCycle(openReq).await().indefinitely();
        assertEquals(201, openResp.getStatus());
        CloseCycle cycle = (CloseCycle) openResp.getEntity();
        assertEquals(CloseStatus.OPEN, cycle.status());

        // Complete mandatory tasks
        resource.completeTask("CC-REST-01", "TASK_SUBLEDGER_CUTOFF", new FinancialCloseResource.CompleteTaskRequest("user1")).await().indefinitely();
        resource.completeTask("CC-REST-01", "TASK_ACCRUALS", new FinancialCloseResource.CompleteTaskRequest("user1")).await().indefinitely();
        resource.completeTask("CC-REST-01", "TASK_DEPRECIATION", new FinancialCloseResource.CompleteTaskRequest("user1")).await().indefinitely();
        resource.completeTask("CC-REST-01", "TASK_RECONCILIATION", new FinancialCloseResource.CompleteTaskRequest("user1")).await().indefinitely();
        resource.completeTask("CC-REST-01", "TASK_LOCK", new FinancialCloseResource.CompleteTaskRequest("user1")).await().indefinitely();

        // Validate
        Response valResp = resource.validate("CC-REST-01").await().indefinitely();
        assertEquals(200, valResp.getStatus());

        // Approve and Lock
        Response lockResp = resource.approveAndLock("CC-REST-01", new FinancialCloseResource.ApproveCloseRequest("CFO")).await().indefinitely();
        assertEquals(200, lockResp.getStatus());
        CloseCycle locked = (CloseCycle) lockResp.getEntity();
        assertEquals(CloseStatus.LOCKED, locked.status());

        // Query by ID
        Response getResp = resource.getById("CC-REST-01").await().indefinitely();
        assertEquals(200, getResp.getStatus());
    }
}
