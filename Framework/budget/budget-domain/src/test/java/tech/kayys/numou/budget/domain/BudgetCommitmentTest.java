package tech.kayys.numou.budget.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Budget Commitment & Encumbrance Engine Test")
class BudgetCommitmentTest {

    @Test
    @DisplayName("Commitment lifecycle: reserve -> commit -> relieve and spend")
    void testBudgetLifecycle() {
        String tenantId = "t1";
        String ledgerId = "PRIMARY";

        // 100M budget for Hardware
        Budget budget = new Budget(
                "BUD-2026-IT", tenantId, ledgerId,
                "2026", "CC-IT",
                "account-1",
                Money.of(new BigDecimal("100000000"), "IDR")
        );

        assertEquals(0, new BigDecimal("100000000").compareTo(budget.availableBalance().amount()));

        // Step 1: Reserve 30M for Requisition
        budget.reservePreEncumbrance(Money.of(new BigDecimal("30000000"), "IDR"));
        assertEquals(0, new BigDecimal("70000000").compareTo(budget.availableBalance().amount()));

        // Step 2: PO issued, convert pre-encumbrance to hard encumbrance
        budget.commitEncumbrance(Money.of(new BigDecimal("30000000"), "IDR"), true);
        assertEquals(0, new BigDecimal("70000000").compareTo(budget.availableBalance().amount()));
        assertEquals(0, new BigDecimal("0").compareTo(budget.preEncumberedAmount().amount()));
        assertEquals(0, new BigDecimal("30000000").compareTo(budget.encumberedAmount().amount()));

        // Step 3: Vendor invoice arrives at 29M (discounted). Relieve 30M, spend 29M.
        budget.relieveAndSpend(Money.of(new BigDecimal("30000000"), "IDR"), Money.of(new BigDecimal("29000000"), "IDR"));
        assertEquals(0, new BigDecimal("71000000").compareTo(budget.availableBalance().amount()));
        assertEquals(0, new BigDecimal("29000000").compareTo(budget.actualSpentAmount().amount()));

        // Step 4: Verify over-budget blocking
        assertThrows(IllegalStateException.class, () ->
                budget.reservePreEncumbrance(Money.of(new BigDecimal("80000000"), "IDR")));
    }
}
