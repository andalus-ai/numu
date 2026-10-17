package tech.kayys.numou.accounting.domain.islamic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahLossResponsibilityTest {
    @Test
    void responsibilityNeedsIndependentEvidenceBackedDeterminationAndChecker() {
        SyirkahLossResponsibility assessment = assessment();
        assertThrows(SecurityException.class, () -> assessment.determine(
                SyirkahLossCause.BREACH_OF_CONTRACT, "manager", List.of("evidence-1"),
                "decision-1", "reporter"));
        assessment.determine(SyirkahLossCause.BREACH_OF_CONTRACT, "manager",
                List.of("evidence-1"), "decision-1", "reviewer");
        assertThrows(SecurityException.class, () -> assessment.approve("reviewer"));
        assessment.approve("checker");
        assessment.markPosted("journal-123");
        assessment.recordSettlement(new BigDecimal("25.00"));
        assertEquals(SyirkahLossResponsibilityStatus.PARTIALLY_SETTLED, assessment.status());
        assessment.recordSettlement(new BigDecimal("75.00"));
        assertEquals(SyirkahLossResponsibilityStatus.SETTLED, assessment.status());
    }

    @Test
    void ordinaryBusinessLossCannotBeApprovedAsPartnerRecovery() {
        SyirkahLossResponsibility assessment = assessment();
        assessment.determine(SyirkahLossCause.ORDINARY_BUSINESS, null,
                List.of("period-close-evidence"), "decision-ordinary", "reviewer");
        assertThrows(IllegalStateException.class, () -> assessment.approve("checker"));
        assertEquals(SyirkahLossResponsibilityStatus.DETERMINED, assessment.status());
    }

    @Test
    void recoveryCannotBePostedBeforeApprovalOrExceedAssessedLoss() {
        SyirkahLossResponsibility assessment = assessment();
        assertThrows(IllegalStateException.class, () -> assessment.markPosted("journal"));
        assessment.determine(SyirkahLossCause.NEGLIGENCE, "manager",
                List.of("evidence-2"), "decision-2", "reviewer");
        assessment.approve("checker");
        assessment.markPosted("journal");
        assertThrows(IllegalArgumentException.class,
                () -> assessment.recordSettlement(new BigDecimal("100.01")));
    }

    @Test
    void determinationRequiresEvidence() {
        SyirkahLossResponsibility assessment = assessment();
        assertThrows(IllegalArgumentException.class, () -> assessment.determine(
                SyirkahLossCause.BREACH_OF_CONTRACT, "manager", List.of(),
                "decision-3", "reviewer"));
    }

    private static SyirkahLossResponsibility assessment() {
        return new SyirkahLossResponsibility("responsibility-1", "tenant-1", "company-1",
                "contract-1", 1, "result-1", "USD", new BigDecimal("100.00"), "reporter");
    }
}
