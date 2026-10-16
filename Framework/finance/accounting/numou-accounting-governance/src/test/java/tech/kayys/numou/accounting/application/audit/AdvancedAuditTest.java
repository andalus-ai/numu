package tech.kayys.syirkah.accounting.application.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.audit.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Audit Platform Tests (draft-audit.md)")
class AdvancedAuditTest {

    private InternalAuditService svc;

    @BeforeEach
    void setUp() {
        svc = new InternalAuditService();
    }

    @Test
    @DisplayName("AuditUniverseScoringEngine computes deterministic risk score and rating")
    void testUniverseScoring() {
        var engine = new AuditUniverseScoringEngine();
        var inputs = new AuditUniverseScoringEngine.Inputs(10, 3, 2, 5_000_000.0);
        // Base: 10 * 2 = 20
        // NC: 3 * 2 = 6
        // Compliance: 2 * 2 = 4
        // Materiality: min(5, 20) = 5
        // Expected = 35 -> MEDIUM
        int score = engine.score(inputs);
        assertEquals(35, score);
        assertEquals(RiskRating.MEDIUM, engine.rating(score));

        // Critical score test
        var critInputs = new AuditUniverseScoringEngine.Inputs(25, 10, 10, 25_000_000.0);
        int critScore = engine.score(critInputs);
        assertTrue(critScore >= 76);
        assertEquals(RiskRating.CRITICAL, engine.rating(critScore));
    }

    @Test
    @DisplayName("AuditPlanBuilder automatically builds risk-weighted annual plan")
    void testAuditPlanBuilder() {
        svc.registerAuditableEntity("ENT-HIGH", "Treasury Ops", "TREASURY", "HIGH");
        svc.registerAuditableEntity("ENT-CRIT", "Wire Payments", "PAYMENTS", "CRITICAL");
        svc.registerAuditableEntity("ENT-LOW", "Archiving", "ADMIN", "LOW");

        svc.rateEntity("ENT-CRIT", 25, 5, 2, 10_000_000.0);
        svc.rateEntity("ENT-HIGH", 15, 2, 1, 2_000_000.0);
        svc.rateEntity("ENT-LOW", 5, 0, 0, 0.0);

        var plan = svc.createAuditPlan("PLAN-2026", "TENANT-1", "COMP-1", 2026, "Annual Internal Audit Plan 2026");
        svc.autoPopulatePlan("PLAN-2026", 200, "LEAD-AUDITOR-JANE");

        assertEquals(3, plan.items().size());
        assertTrue(plan.totalEstimatedHours() > 100);
        assertEquals(AuditPlanStatus.DRAFT, plan.status());

        svc.approveAuditPlan("PLAN-2026", "AUDIT_COMMITTEE_CHAIR");
        assertEquals(AuditPlanStatus.APPROVED, plan.status());

        svc.activateAuditPlan("PLAN-2026");
        assertEquals(AuditPlanStatus.ACTIVE, plan.status());
    }

    @Test
    @DisplayName("Working papers can be attached, indexed, and reviewed")
    void testWorkingPapers() {
        svc.registerAuditableEntity("ENT-P2P", "P2P Process", "PROCURE_TO_PAY", "HIGH");
        var eng = svc.openEngagement("ENT-P2P", "ENG-WP", "P2P Controls Review", "AUDITOR-1");

        var wp = svc.attachWorkingPaper("ENG-WP", "Sample 3-Way Match Verification", "WP-P2P-01", "AUDITOR-1", "DOC-EXT-9988");
        assertNotNull(wp);
        assertFalse(wp.isReviewed());

        svc.reviewWorkingPaper(wp.id().value(), "AUDIT_MANAGER");
        assertTrue(wp.isReviewed());
        assertEquals("AUDIT_MANAGER", wp.reviewedBy());

        var papers = svc.getWorkingPapers("ENG-WP");
        assertEquals(1, papers.size());
    }

    @Test
    @DisplayName("Recommendation and Follow-up complete remediation lifecycle")
    void testRecommendationAndFollowUp() {
        svc.registerAuditableEntity("ENT-GL", "General Ledger", "R2R", "MEDIUM");
        var eng = svc.openEngagement("ENT-GL", "ENG-REC", "GL Reconciliation Audit", "AUDITOR-2");
        svc.advanceEngagement("ENG-REC", AuditEngagement.Phase.FIELDWORK);

        var finding = svc.recordFinding("ENG-REC", "Bank reconciliations overdue by >30 days",
                AuditFinding.Severity.SIGNIFICANT, "Automate bank feeds");

        var rec = svc.issueRecommendation(finding.findingId(), "Deploy automated MT940 statement feed",
                "TREASURY_HEAD", LocalDate.now().plusMonths(2));
        assertEquals(RecommendationStatus.ISSUED, rec.status());

        svc.acceptRecommendation(rec.id().value(), "TREASURY_HEAD");
        assertEquals(RecommendationStatus.ACCEPTED, rec.status());

        var fu = svc.scheduleFollowUp(rec.id().value(), "INTERNAL_AUDITOR", LocalDate.now().plusMonths(2));
        assertEquals(FollowUpStatus.SCHEDULED, fu.status());

        svc.markRecommendationImplemented(rec.id().value(), "MT940 feed active and operational");
        assertEquals(RecommendationStatus.IMPLEMENTED, rec.status());

        svc.completeFollowUp(fu.id().value(), "Verified automated reconciliation logs");
        assertEquals(FollowUpStatus.COMPLETED, fu.status());
    }
}
