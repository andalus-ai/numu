
package tech.kayys.syirkah.accounting.application.procurement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Procurement 3-Way Matching Engine & Module Tests")
class MatchingEngineTest {

    @Test
    @DisplayName("3-way matching returns MATCHED for exact quantities and prices")
    void testExactMatch() {
        MatchingEngine engine = MatchingEngine.defaultEngine();

        MatchResult result = engine.match3Way(
                "PO-001", new BigDecimal("100"), new BigDecimal("50.00"),
                "GR-001", new BigDecimal("100"),
                "INV-001", new BigDecimal("100"), new BigDecimal("50.00")
        );

        assertEquals(MatchOutcome.MATCHED, result.outcome());
        assertTrue(result.isPayable());
    }

    @Test
    @DisplayName("3-way matching returns TOLERANCE for small variances within threshold")
    void testToleranceMatch() {
        MatchingEngine engine = MatchingEngine.defaultEngine(); // 1% qty, 2% price

        MatchResult result = engine.match3Way(
                "PO-001", new BigDecimal("100"), new BigDecimal("50.00"),
                "GR-001", new BigDecimal("100"),
                "INV-001", new BigDecimal("100.5"), new BigDecimal("50.50") // 0.5% qty, 1% price
        );

        assertEquals(MatchOutcome.TOLERANCE, result.outcome());
        assertTrue(result.isPayable());
    }

    @Test
    @DisplayName("3-way matching returns MISMATCH when variance exceeds threshold")
    void testMismatch() {
        MatchingEngine engine = MatchingEngine.defaultEngine();

        MatchResult result = engine.match3Way(
                "PO-001", new BigDecimal("100"), new BigDecimal("50.00"),
                "GR-001", new BigDecimal("100"),
                "INV-001", new BigDecimal("110"), new BigDecimal("60.00") // 10% qty, 20% price
        );

        assertEquals(MatchOutcome.MISMATCH, result.outcome());
        assertFalse(result.isPayable());
    }
}
