package tech.kayys.syirkah.accounting.application.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.quality.CapaAction;
import tech.kayys.syirkah.accounting.domain.quality.Inspection;
import tech.kayys.syirkah.accounting.domain.quality.NonConformance;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QualityService")
class QualityServiceTest {

    private QualityService svc;

    @BeforeEach
    void setUp() {
        svc = new QualityService();
    }

    // ── Inspection ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createInspection returns PENDING inspection")
    void createInspection_pending() {
        var insp = svc.createInspection("LOT-001", "item-A");
        assertNotNull(insp);
        assertEquals(Inspection.Status.PENDING, insp.status());
        assertEquals("LOT-001", insp.lotNumber());
    }

    @Test
    @DisplayName("passInspection transitions to PASSED")
    void passInspection() {
        var insp = svc.createInspection("LOT-002", "item-B");
        svc.passInspection(insp.id());
        assertEquals(Inspection.Status.PASSED, insp.status());
    }

    @Test
    @DisplayName("failInspection transitions to FAILED")
    void failInspection() {
        var insp = svc.createInspection("LOT-003", "item-C");
        svc.failInspection(insp.id());
        assertEquals(Inspection.Status.FAILED, insp.status());
    }

    @Test
    @DisplayName("getInspection throws for unknown ID")
    void getInspection_notFound() {
        assertThrows(IllegalArgumentException.class, () -> svc.getInspection(
                new tech.kayys.syirkah.accounting.domain.quality.InspectionId("bad-id")));
    }

    // ── Non-Conformance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordNcr creates NonConformance in OPEN state")
    void recordNcr_open() {
        var insp = svc.createInspection("LOT-004", "item-D");
        svc.failInspection(insp.id());
        var ncr = svc.recordNcr(insp.id(), "Dimensional out-of-spec",
                NonConformance.Severity.MAJOR);
        assertNotNull(ncr);
        assertNull(ncr.disposition()); // not yet dispositioned
    }

    @Test
    @DisplayName("recordNcr on passing inspection throws")
    void recordNcr_onPassedInspection_throws() {
        var insp = svc.createInspection("LOT-005", "item-E");
        svc.passInspection(insp.id());
        assertThrows(IllegalStateException.class,
                () -> svc.recordNcr(insp.id(), "Should fail", NonConformance.Severity.MINOR));
    }

    // ── CAPA ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createCapa returns OPEN CapaAction")
    void createCapa_open() {
        var insp = svc.createInspection("LOT-006", "item-F");
        svc.failInspection(insp.id());
        var ncr = svc.recordNcr(insp.id(), "Surface scratch", NonConformance.Severity.MINOR);
        var capa = svc.createCapa(ncr, "Retrain operators", "ENG-007");
        assertNotNull(capa);
        assertEquals(CapaAction.Status.OPEN, capa.status());
        assertEquals("ENG-007", capa.ownerId());
    }

    @Test
    @DisplayName("capa lifecycle: OPEN → IN_PROGRESS → RESOLVED → VERIFIED")
    void capa_fullLifecycle() {
        var insp = svc.createInspection("LOT-007", "item-G");
        svc.failInspection(insp.id());
        var ncr = svc.recordNcr(insp.id(), "Wrong label", NonConformance.Severity.MINOR);
        var capa = svc.createCapa(ncr, "Update labelling procedure", "QA-001");

        capa.startProgress();
        assertEquals(CapaAction.Status.IN_PROGRESS, capa.status());

        capa.resolve();
        assertEquals(CapaAction.Status.RESOLVED, capa.status());

        capa.verify();
        assertEquals(CapaAction.Status.VERIFIED, capa.status());
    }
}
