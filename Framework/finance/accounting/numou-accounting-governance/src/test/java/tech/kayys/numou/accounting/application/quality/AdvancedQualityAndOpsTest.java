package tech.kayys.syirkah.accounting.application.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.legal.LegalService;
import tech.kayys.syirkah.accounting.application.maintenance.MaintenanceService;
import tech.kayys.syirkah.accounting.application.risk.RiskService;
import tech.kayys.syirkah.accounting.domain.legal.ComplianceStatus;
import tech.kayys.syirkah.accounting.domain.legal.LegalContract;
import tech.kayys.syirkah.accounting.domain.maintenance.MaintenanceType;
import tech.kayys.syirkah.accounting.domain.maintenance.MaintenanceWorkOrder;
import tech.kayys.syirkah.accounting.domain.maintenance.MeterUnit;
import tech.kayys.syirkah.accounting.domain.quality.*;
import tech.kayys.syirkah.accounting.domain.risk.RiskHeatMap;
import tech.kayys.syirkah.accounting.domain.risk.RiskIncident;
import tech.kayys.syirkah.accounting.domain.risk.RiskItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Quality, Maintenance, Legal, and Risk Tests (darft-capa.md)")
class AdvancedQualityAndOpsTest {

    private QualityService qualityService;
    private MaintenanceService maintenanceService;
    private LegalService legalService;
    private RiskService riskService;

    @BeforeEach
    void setUp() {
        qualityService = new QualityService();
        maintenanceService = new MaintenanceService();
        legalService = new LegalService();
        riskService = new RiskService();
    }

    // ── Quality / CAPA ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Specification tolerance evaluation and NCR disposition matrix")
    void testSpecificationAndNcrDisposition() {
        var spec = qualityService.createSpecification("SPEC-STEEL-01", "RAW-STEEL-ROD", "Steel Rod Tensile & Diameter", 1);
        spec.addParameter(new SpecificationParameter("diameter_mm", 10.0, 9.8, 10.2, "mm", true));
        spec.addParameter(new SpecificationParameter("tensile_strength_mpa", 500.0, 480.0, 550.0, "MPa", true));
        spec.activate();

        var insp = qualityService.createInspection("LOT-BATCH-2026-A", "INSPECTOR-BOB");

        // Test failing evaluation
        var failResult = qualityService.evaluateAgainstSpec(insp.id(), "SPEC-STEEL-01", Map.of(
                "diameter_mm", 10.5, // out of spec!
                "tensile_strength_mpa", 510.0
        ));
        assertFalse(failResult.passed());
        assertEquals(Inspection.Status.FAILED, insp.status());

        // Raise NCR and assign disposition
        var ncr = qualityService.recordNcr(insp.id(), "Diameter out of spec on Lot A", NonConformance.Severity.MAJOR);
        qualityService.dispositionNcr(ncr.id(), DispositionType.REWORK, "QA_MANAGER", "Re-machine to 9.9mm", BigDecimal.valueOf(150.00));

        assertTrue(ncr.isDispositioned());
        assertEquals(DispositionType.REWORK, ncr.disposition().dispositionType());
        assertEquals(BigDecimal.valueOf(150.00), ncr.disposition().costImpact());
    }

    @Test
    @DisplayName("CAPA 5-Whys root cause analysis lifecycle")
    void testCapaFiveWhys() {
        var capa = qualityService.createCapa("Machine Calibration Drift", "Recalibrate CNC tool weekly", "PLANT_ENG", LocalDate.now().plusMonths(1));
        assertEquals(CapaAction.Status.OPEN, capa.status());

        capa.recordFiveWhys(List.of(
                "Why 1: Tool cut beyond diameter tolerance",
                "Why 2: Cutting head had excessive vibration",
                "Why 3: Bearing had worn prematurely",
                "Why 4: Lubrication cycle was skipped during shift change",
                "Why 5: No automated lubrication sensor installed"
        ), "Root cause: Lack of automatic lubrication check");

        assertEquals(CapaAction.Status.ROOT_CAUSE_ANALYSIS, capa.status());
        assertEquals(5, capa.fiveWhys().size());

        capa.addCorrectiveAction("Install automated lubrication interlock sensor");
        capa.addPreventiveAction("Update shift-change checklist and PLC lock");
        assertEquals(CapaAction.Status.ACTION_PLANNING, capa.status());

        capa.advanceToImplementation();
        assertEquals(CapaAction.Status.IMPLEMENTATION, capa.status());

        capa.advanceToVerification();
        assertEquals(CapaAction.Status.VERIFICATION, capa.status());

        capa.close("Sensor installed and verified during 100 runs");
        assertEquals(CapaAction.Status.CLOSED, capa.status());
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Maintenance work order tracks parts, labor, and capitalizable status")
    void testMaintenancePartsAndLabor() {
        var wo = maintenanceService.createWorkOrder("ASSET-TURBINE-01", "Overhaul rotor blades", MaintenanceWorkOrder.Priority.HIGH);
        wo.setCapitalizable(true);

        maintenanceService.addPartToWorkOrder(wo.id(), "PART-ROTOR-01", "Titanium blade set", 2, BigDecimal.valueOf(1200.00));
        maintenanceService.addPartToWorkOrder(wo.id(), "PART-GASKET-02", "High-temp seal kit", 4, BigDecimal.valueOf(75.00));
        maintenanceService.addLaborToWorkOrder(wo.id(), "TECH-DAVE", 10.5, BigDecimal.valueOf(80.00));

        // Parts: (2 * 1200) + (4 * 75) = 2400 + 300 = 2700
        // Labor: 10.5 * 80 = 840
        // Total = 3540
        assertEquals(0, BigDecimal.valueOf(2700.00).compareTo(wo.totalPartsCost()));
        assertEquals(0, BigDecimal.valueOf(840.00).compareTo(wo.totalLaborCost()));
        assertEquals(0, BigDecimal.valueOf(3540.00).compareTo(wo.totalActualCost()));
        assertTrue(wo.isCapitalizable());
    }

    @Test
    @DisplayName("Meter readings recorded and tracked")
    void testMeterReadings() {
        var reading = maintenanceService.recordMeterReading("ASSET-PUMP-02", MeterUnit.HOURS, 1250.5, "OPERATOR_ALICE");
        assertNotNull(reading);
        assertEquals(1, maintenanceService.getMeterReadings("ASSET-PUMP-02").size());
        assertEquals(1250.5, reading.value());
    }

    // ── Legal ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Legal contracts track renewal notices and compliance calendar entries")
    void testLegalContractsAndCompliance() {
        var contract = legalService.registerContract("CTR-VENDOR-CLOUD", "Cloud Infrastructure SLA",
                "CloudCorp", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));
        contract.activate();
        contract.configureRenewal(true, 60);

        // Notice deadline = 2026-12-31 - 60 days = 2026-11-01
        assertEquals(LocalDate.of(2026, 11, 1), contract.noticeDeadline());

        // On 2026-11-15, notice is due
        assertTrue(contract.isRenewalNoticeDue(LocalDate.of(2026, 11, 15)));
        // On 2026-06-01, notice is not yet due
        assertFalse(contract.isRenewalNoticeDue(LocalDate.of(2026, 6, 1)));

        // Obligation automatically registers compliance calendar entry
        legalService.addObligation("CTR-VENDOR-CLOUD", "Quarterly SOC 2 Type II audit delivery", LocalDate.of(2026, 3, 31));
        var entries = legalService.getCalendarEntries("CTR-VENDOR-CLOUD");
        assertEquals(1, entries.size());
        assertEquals(ComplianceStatus.PENDING, entries.get(0).status());

        entries.get(0).markCompleted();
        assertEquals(ComplianceStatus.COMPLETED, entries.get(0).status());
    }

    // ── Risk ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5x5 Risk Heatmap evaluation and incident recording")
    void testRiskHeatmapAndIncidents() {
        var scoreLow = riskService.scoreRisk(1, 2);
        assertEquals(2, scoreLow.score());
        assertEquals(RiskHeatMap.HeatBand.LOW, scoreLow.band());

        var scoreCrit = riskService.scoreRisk(5, 4);
        assertEquals(20, scoreCrit.score());
        assertEquals(RiskHeatMap.HeatBand.CRITICAL, scoreCrit.band());

        var risk = riskService.registerRisk("RSK-CYBER-01", "CYBERSECURITY", "Ransomware Attack", RiskItem.Level.CRITICAL);
        var incident = riskService.recordIncident("RSK-CYBER-01", "Phishing Email Compromise",
                "Finance user clicked simulated payload", BigDecimal.valueOf(5000.00), "SOC_ANALYST");

        assertNotNull(incident);
        assertEquals(1, riskService.getIncidents("RSK-CYBER-01").size());
        assertEquals(RiskIncident.IncidentStatus.REPORTED, incident.status());
    }
}
