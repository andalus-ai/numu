
package tech.kayys.syirkah.accounting.application.consolidation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.consolidation.EliminationEngine;
import tech.kayys.syirkah.accounting.domain.consolidation.ConsolidationRun;
import tech.kayys.syirkah.accounting.domain.consolidation.ConsolidationRunStatus;
import tech.kayys.syirkah.accounting.domain.consolidation.GroupHierarchy;
import tech.kayys.syirkah.accounting.domain.consolidation.GroupMember;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Consolidation Platform Lifecycle & Elimination Test")
class ConsolidationLifecycleTest {

    @Test
    @DisplayName("Group hierarchy setup and non-controlling interest percentage")
    void testGroupHierarchy() {
        GroupMember parent = new GroupMember(new TenantRef("parent-corp"), "Parent Holding Corp", new BigDecimal("100.00"), true);
        GroupHierarchy hierarchy = new GroupHierarchy("GRP-01", "Andalus Group", parent);

        GroupMember sub = new GroupMember(new TenantRef("sub-tech"), "Tech Subsidiary", new BigDecimal("80.00"), false);
        hierarchy.addSubsidiary(sub);

        assertEquals(1, hierarchy.subsidiaries().size());
        assertEquals(0, new BigDecimal("20.00").compareTo(sub.nonControllingInterestPercentage()));
    }

    @Test
    @DisplayName("ConsolidationRun lifecycle and reciprocal balance elimination")
    void testConsolidationRun() {
        TenantRef parentTenant = new TenantRef("parent-corp");
        ConsolidationRun run = new ConsolidationRun(
                "CONS-2026-Q3", parentTenant, new LedgerId("CONSOLIDATION"),
                "2026", 3, LocalDate.now()
        );

        assertEquals(ConsolidationRunStatus.DRAFT, run.status());

        run.startCollection();
        assertEquals(ConsolidationRunStatus.COLLECTING, run.status());

        // Eliminate 50M reciprocal balance
        EliminationEngine engine = new EliminationEngine();
        var match = engine.eliminateReciprocalBalance(
                "ELIM-001",
                AccountId.generate(),
                AccountId.generate(),
                Money.of(new BigDecimal("50000000"), "IDR"),
                Money.of(new BigDecimal("50000000"), "IDR")
        );

        assertTrue(match.balanced());
        assertEquals(0, BigDecimal.ZERO.compareTo(match.varianceAmount()));

        run.addElimination(match.eliminationEntry());
        assertEquals(ConsolidationRunStatus.ELIMINATING, run.status());
        assertEquals(1, run.eliminations().size());

        run.completeEliminations();
        assertEquals(ConsolidationRunStatus.TRANSLATING, run.status());

        run.closeConsolidation();
        assertEquals(ConsolidationRunStatus.CLOSING, run.status());

        run.publish();
        assertEquals(ConsolidationRunStatus.PUBLISHED, run.status());
    }
}
