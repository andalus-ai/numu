
package tech.kayys.syirkah.accounting.application.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FinancialRuleEngine Unit Tests")
class FinancialRuleEngineTest {

    record PaymentRuleContext(
            TenantRef tenantId,
            LedgerId ledgerId,
            BigDecimal amount
    ) implements RuleContext {}

    @Test
    @DisplayName("evaluate rule engine passes under threshold and blocks when threshold exceeded")
    void testRuleEngineEvaluation() {
        FinancialRuleEngine engine = new DefaultFinancialRuleEngine();

        // Register a CFO approval rule for payments > 100M
        engine.registerRule(PaymentRuleContext.class, new FinancialRule<>() {
            @Override
            public String code() { return "HIGH_VALUE_PAYMENT"; }
            @Override
            public String description() { return "High value payment requires CFO approval"; }
            @Override
            public int priority() { return 1; }
            @Override
            public RuleResult evaluate(PaymentRuleContext context) {
                if (context.amount().compareTo(new BigDecimal("100000000")) > 0) {
                    return RuleResult.trigger(code(), "Amount exceeds 100M",
                            RuleResult.Severity.BLOCKING, RuleResult.RuleAction.REQUIRE_ADDITIONAL_APPROVAL);
                }
                return RuleResult.pass(code());
            }
        });

        PaymentRuleContext normalPayment = new PaymentRuleContext(
                new TenantRef("t1"), new LedgerId("PRIMARY"), new BigDecimal("50000000"));
        PaymentRuleContext highPayment = new PaymentRuleContext(
                new TenantRef("t1"), new LedgerId("PRIMARY"), new BigDecimal("150000000"));

        assertFalse(engine.isBlocked(normalPayment));
        assertTrue(engine.isBlocked(highPayment));

        List<RuleResult> results = engine.evaluate(highPayment);
        assertEquals(1, results.size());
        assertEquals(RuleResult.RuleAction.REQUIRE_ADDITIONAL_APPROVAL, results.getFirst().action());
    }
}
