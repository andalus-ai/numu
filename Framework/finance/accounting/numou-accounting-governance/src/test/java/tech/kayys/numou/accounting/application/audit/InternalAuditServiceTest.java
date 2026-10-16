package tech.kayys.syirkah.accounting.application.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.audit.AuditEngagement;
import tech.kayys.syirkah.accounting.domain.audit.AuditFinding;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InternalAuditService")
class InternalAuditServiceTest {

    private InternalAuditService svc;

    @BeforeEach
    void setUp() {
        svc = new InternalAuditService();
    }

    // ── Audit Universe ────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerAuditableEntity stores entity in universe")
    void registerEntity() {
        var entity = svc.registerAuditableEntity("ENT-001", "PROCURE_TO_PAY", "HIGH");
        assertNotNull(entity);
        assertEquals("ENT-001", entity.entityId());
        assertEquals("HIGH", entity.riskRating());
    }

    @Test
    @DisplayName("registerAuditableEntity with blank ID throws")
    void registerEntity_blankId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.registerAuditableEntity("", "P2P", "MEDIUM"));
    }

    @Test
    @DisplayName("getAuditableEntity throws for unknown entity")
    void getEntity_notFound() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.getAuditableEntity("MISSING"));
    }

    @Test
    @DisplayName("getAuditUniverse returns all registered entities")
    void getAuditUniverse() {
        svc.registerAuditableEntity("ENT-002", "ORDER_TO_CASH", "MEDIUM");
        svc.registerAuditableEntity("ENT-003", "RECORD_TO_REPORT", "LOW");
        assertEquals(2, svc.getAuditUniverse().size());
    }

    // ── Engagement Lifecycle ──────────────────────────────────────────────────

    @Test
    @DisplayName("openEngagement creates engagement in PLANNING phase")
    void openEngagement_planning() {
        svc.registerAuditableEntity("ENT-004", "P2P", "HIGH");
        var eng = svc.openEngagement("ENT-004", "ENG-001", "Q1 Procurement Audit", "AUDITOR-1");
        assertNotNull(eng);
        assertEquals(AuditEngagement.Phase.PLANNING, eng.currentPhase());
        assertEquals("ENT-004", eng.entityId());
    }

    @Test
    @DisplayName("openEngagement for non-universe entity throws")
    void openEngagement_unknownEntity_throws() {
        assertThrows(IllegalStateException.class,
                () -> svc.openEngagement("NOT-IN-UNIVERSE", "ENG-X", "Test", "AUD-1"));
    }

    @Test
    @DisplayName("advanceEngagement progresses through all phases")
    void advanceEngagement_fullLifecycle() {
        svc.registerAuditableEntity("ENT-005", "HR", "MEDIUM");
        var eng = svc.openEngagement("ENT-005", "ENG-002", "HR Audit", "AUD-2");

        svc.advanceEngagement("ENG-002", AuditEngagement.Phase.FIELDWORK);
        assertEquals(AuditEngagement.Phase.FIELDWORK, eng.currentPhase());

        svc.advanceEngagement("ENG-002", AuditEngagement.Phase.REPORTING);
        assertEquals(AuditEngagement.Phase.REPORTING, eng.currentPhase());

        svc.advanceEngagement("ENG-002", AuditEngagement.Phase.FOLLOW_UP);
        assertEquals(AuditEngagement.Phase.FOLLOW_UP, eng.currentPhase());

        svc.advanceEngagement("ENG-002", AuditEngagement.Phase.CLOSED);
        assertEquals(AuditEngagement.Phase.CLOSED, eng.currentPhase());
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordFinding in FIELDWORK phase succeeds")
    void recordFinding_fieldwork() {
        svc.registerAuditableEntity("ENT-006", "AP", "HIGH");
        var eng = svc.openEngagement("ENT-006", "ENG-003", "AP Audit", "AUD-3");
        svc.advanceEngagement("ENG-003", AuditEngagement.Phase.FIELDWORK);

        var finding = svc.recordFinding("ENG-003",
                "Invoice approved without PO match",
                AuditFinding.Severity.HIGH,
                "Implement 3-way matching control");

        assertNotNull(finding);
        assertEquals(AuditFinding.Severity.HIGH, finding.severity());
        assertEquals("ENG-003", finding.engagementId());
    }

    @Test
    @DisplayName("recordFinding in PLANNING phase throws")
    void recordFinding_planning_throws() {
        svc.registerAuditableEntity("ENT-007", "GL", "LOW");
        svc.openEngagement("ENT-007", "ENG-004", "GL Audit", "AUD-4");

        assertThrows(IllegalStateException.class,
                () -> svc.recordFinding("ENG-004",
                        "Finding in planning — invalid",
                        AuditFinding.Severity.LOW,
                        "N/A"));
    }

    @Test
    @DisplayName("recordFinding in CLOSED phase throws")
    void recordFinding_closed_throws() {
        svc.registerAuditableEntity("ENT-008", "AR", "MEDIUM");
        svc.openEngagement("ENT-008", "ENG-005", "AR Audit", "AUD-5");
        svc.advanceEngagement("ENG-005", AuditEngagement.Phase.FIELDWORK);
        svc.advanceEngagement("ENG-005", AuditEngagement.Phase.REPORTING);
        svc.advanceEngagement("ENG-005", AuditEngagement.Phase.FOLLOW_UP);
        svc.advanceEngagement("ENG-005", AuditEngagement.Phase.CLOSED);

        assertThrows(IllegalStateException.class,
                () -> svc.recordFinding("ENG-005",
                        "Post-close finding — invalid",
                        AuditFinding.Severity.CRITICAL,
                        "N/A"));
    }

    @Test
    @DisplayName("getFindingsBySeverity returns cross-engagement filtered results")
    void getFindingsBySeverity() {
        svc.registerAuditableEntity("ENT-009", "TREASURY", "CRITICAL");
        svc.registerAuditableEntity("ENT-010", "PAYROLL", "HIGH");
        svc.openEngagement("ENT-009", "ENG-006", "Treasury Audit", "AUD-6");
        svc.openEngagement("ENT-010", "ENG-007", "Payroll Audit", "AUD-7");
        svc.advanceEngagement("ENG-006", AuditEngagement.Phase.FIELDWORK);
        svc.advanceEngagement("ENG-007", AuditEngagement.Phase.FIELDWORK);

        svc.recordFinding("ENG-006", "Unauthorized wire transfer",
                AuditFinding.Severity.CRITICAL, "Implement dual authorization");
        svc.recordFinding("ENG-006", "Minor doc gap",
                AuditFinding.Severity.LOW, "Update policy");
        svc.recordFinding("ENG-007", "Payroll segregation gap",
                AuditFinding.Severity.CRITICAL, "Implement SoD");

        var criticals = svc.getFindingsBySeverity(AuditFinding.Severity.CRITICAL);
        assertEquals(2, criticals.size());
    }
}
