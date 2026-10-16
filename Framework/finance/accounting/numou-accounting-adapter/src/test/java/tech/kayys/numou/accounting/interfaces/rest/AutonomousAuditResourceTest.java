package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.rule.DeclarativeRuleEngine;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousAuditResourceTest {

    private AutonomousAuditResource resource;

    @BeforeEach
    void setUp() {
        resource = new AutonomousAuditResource();
        resource.ruleEngine = new DeclarativeRuleEngine();
    }

    @Test
    void testAuditAnalysis() {
        // High anomaly: weekend + large round amount + 60 lines
        var req = new AutonomousAuditResource.AuditAnalysisRequest(
                "JE-999", BigDecimal.valueOf(50000), 60, true, "system_admin");

        Response resp = resource.analyzeJournalEntry(req).await().indefinitely();
        assertEquals(200, resp.getStatus());
        var entity = (AutonomousAuditResource.AuditAnalysisResponse) resp.getEntity();
        assertTrue(entity.flagged());
        assertTrue(entity.anomalyScore() >= 0.50);
        assertEquals(3, entity.findings().size());

        // Low anomaly: weekday + normal lines + non-round amount
        var normalReq = new AutonomousAuditResource.AuditAnalysisRequest(
                "JE-100", BigDecimal.valueOf(1234.56), 2, false, "regular_accountant");
        Response normalResp = resource.analyzeJournalEntry(normalReq).await().indefinitely();
        assertEquals(200, normalResp.getStatus());
        var normalEntity = (AutonomousAuditResource.AuditAnalysisResponse) normalResp.getEntity();
        assertFalse(normalEntity.flagged());
        assertEquals(0.0, normalEntity.anomalyScore());
    }

    @Test
    void testAutonomousReconciliation() {
        Response resp = resource.triggerAutonomousReconciliation().await().indefinitely();
        assertEquals(200, resp.getStatus());
        var map = (Map<?, ?>) resp.getEntity();
        assertEquals("RECONCILED", map.get("status"));
    }
}
