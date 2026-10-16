package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.quality.QualityService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QualityResourceTest")
class QualityResourceTest {

    private QualityResource resource;
    private QualityService service;

    @BeforeEach
    void setUp() {
        service = new QualityService();
        resource = new QualityResource();
        resource.qualityService = service;
    }

    @Test
    @DisplayName("Inspection and CAPA flow via REST resource")
    void testQualityLifecycle() {
        // 1. Create Inspection
        var createReq = new QualityResource.CreateInspectionRequest("LOT-999", "ITEM-XYZ");
        Response createResp = resource.createInspection(createReq);
        assertEquals(201, createResp.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) createResp.getEntity();
        String inspectionId = (String) body.get("inspectionId");
        assertNotNull(inspectionId);
        assertEquals("PENDING", body.get("status"));

        // 2. Fail inspection
        Response failResp = resource.failInspection(inspectionId);
        assertEquals(200, failResp.getStatus());

        // 3. Record NCR
        var ncrReq = new QualityResource.RecordNcrRequest("Defect in finish", "MAJOR");
        Response ncrResp = resource.recordNcr(inspectionId, ncrReq);
        assertEquals(201, ncrResp.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> ncrBody = (Map<String, Object>) ncrResp.getEntity();
        String ncrId = (String) ncrBody.get("ncrId");
        assertNotNull(ncrId);

        // 4. Create CAPA
        var capaReq = new QualityResource.CreateCapaRequest(inspectionId, ncrId, "Recalibrate machine", "QA-LEAD");
        Response capaResp = resource.createCapa(capaReq);
        assertEquals(201, capaResp.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> capaBody = (Map<String, Object>) capaResp.getEntity();
        assertNotNull(capaBody.get("capaId"));
        assertEquals("OPEN", capaBody.get("status"));
    }
}
