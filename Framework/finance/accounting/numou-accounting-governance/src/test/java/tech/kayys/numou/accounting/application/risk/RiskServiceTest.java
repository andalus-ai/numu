package tech.kayys.syirkah.accounting.application.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.risk.RiskItem;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RiskService")
class RiskServiceTest {

    private RiskService svc;

    @BeforeEach
    void setUp() {
        svc = new RiskService();
    }

    @Test
    @DisplayName("registerRisk creates a RiskItem with inherent and residual set equal initially")
    void registerRisk_initial() {
        var risk = svc.registerRisk("RSK-001", "Supplier concentration risk",
                RiskItem.Level.HIGH);
        assertNotNull(risk);
        assertEquals("RSK-001", risk.riskId());
        assertEquals(RiskItem.Level.HIGH, risk.inherentRisk());
        // residualRisk defaults to inherent at registration
        assertEquals(RiskItem.Level.HIGH, risk.residualRisk());
    }

    @Test
    @DisplayName("assessControl with effective=true on CRITICAL risk reduces residual to HIGH")
    void assessControl_effective_critical() {
        var risk = svc.registerRisk("RSK-002", "Data breach", RiskItem.Level.CRITICAL);
        var assessment = svc.assessControl("RSK-002", "CTL-001", "Encryption at rest", true);
        assertTrue(assessment.effective());
        assertEquals(RiskItem.Level.HIGH, risk.residualRisk());
    }

    @Test
    @DisplayName("assessControl with effective=true on HIGH risk reduces residual to MEDIUM")
    void assessControl_effective_high() {
        var risk = svc.registerRisk("RSK-003", "Process failure", RiskItem.Level.HIGH);
        svc.assessControl("RSK-003", "CTL-002", "Dual approval controls", true);
        assertEquals(RiskItem.Level.MEDIUM, risk.residualRisk());
    }

    @Test
    @DisplayName("assessControl with effective=true on MEDIUM or LOW risk reduces residual to LOW")
    void assessControl_effective_medium() {
        var risk = svc.registerRisk("RSK-004", "Minor reporting delay", RiskItem.Level.MEDIUM);
        svc.assessControl("RSK-004", "CTL-003", "Auto-escalation alerts", true);
        assertEquals(RiskItem.Level.LOW, risk.residualRisk());
    }

    @Test
    @DisplayName("assessControl with effective=false does NOT reduce residual risk")
    void assessControl_notEffective() {
        var risk = svc.registerRisk("RSK-005", "Fraud risk", RiskItem.Level.CRITICAL);
        svc.assessControl("RSK-005", "CTL-004", "Manual review", false);
        // residual remains at inherent since control is ineffective
        assertEquals(RiskItem.Level.CRITICAL, risk.residualRisk());
    }

    @Test
    @DisplayName("getRisk throws for unknown ID")
    void getRisk_notFound() {
        assertThrows(IllegalArgumentException.class, () -> svc.getRisk("MISSING"));
    }

    @Test
    @DisplayName("listRisks returns all registered risks")
    void listRisks() {
        svc.registerRisk("RSK-006", "Risk A", RiskItem.Level.LOW);
        svc.registerRisk("RSK-007", "Risk B", RiskItem.Level.MEDIUM);
        assertEquals(2, svc.listRisks().size());
    }
}
