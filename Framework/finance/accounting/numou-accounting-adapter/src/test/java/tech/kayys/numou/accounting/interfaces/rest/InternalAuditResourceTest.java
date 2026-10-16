package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.audit.InternalAuditService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InternalAuditResourceTest")
class InternalAuditResourceTest {

    private InternalAuditResource resource;
    private InternalAuditService service;

    @BeforeEach
    void setUp() {
        service = new InternalAuditService();
        resource = new InternalAuditResource();
        resource.auditService = service;
    }

    @Test
    @DisplayName("Universe, Engagement and Finding flow via REST resource")
    void testAuditLifecycle() {
        // 1. Register Auditable Entity
        var regReq = new InternalAuditResource.RegisterEntityRequest("PROC-P2P", "PROCURE_TO_PAY", "HIGH");
        Response regResp = resource.registerEntity(regReq);
        assertEquals(201, regResp.getStatus());

        // 2. List Universe
        Response listUniverseResp = resource.listUniverse();
        assertEquals(200, listUniverseResp.getStatus());
        @SuppressWarnings("unchecked")
        List<?> universe = (List<?>) listUniverseResp.getEntity();
        assertEquals(1, universe.size());

        // 3. Open Engagement
        var openReq = new InternalAuditResource.OpenEngagementRequest("PROC-P2P", "ENG-001", "P2P Review", "AUDITOR-1");
        Response openResp = resource.openEngagement(openReq);
        assertEquals(201, openResp.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> openBody = (Map<String, Object>) openResp.getEntity();
        assertEquals("PLANNING", openBody.get("phase"));

        // 4. Advance Engagement to FIELDWORK
        var advReq = new InternalAuditResource.AdvancePhaseRequest("FIELDWORK");
        Response advResp = resource.advanceEngagement("ENG-001", advReq);
        assertEquals(200, advResp.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> advBody = (Map<String, Object>) advResp.getEntity();
        assertEquals("FIELDWORK", advBody.get("phase"));

        // 5. Record Finding
        var findReq = new InternalAuditResource.RecordFindingRequest("Missing PO approval", "HIGH", "Enforce 3-way match");
        Response findResp = resource.recordFinding("ENG-001", findReq);
        assertEquals(201, findResp.getStatus());

        // 6. Get Findings
        Response getFindingsResp = resource.getFindings("ENG-001");
        assertEquals(200, getFindingsResp.getStatus());
        @SuppressWarnings("unchecked")
        List<?> findings = (List<?>) getFindingsResp.getEntity();
        assertEquals(1, findings.size());
    }
}
