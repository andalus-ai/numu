package tech.kayys.syirkah.accounting.application.rule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeclarativeRuleEngineTest {

    @Test
    void evaluates_threshold_rule_matching() {
        var engine = new DeclarativeRuleEngine();
        var limitRule = new RuleDefinition(
                RuleId.generate(),
                RuleCode.of("PAYMENT_LIMIT"),
                "High value payment check",
                "amount",
                ">",
                new BigDecimal("10000"),
                RuleResult.Severity.BLOCKING,
                RuleResult.RuleAction.REJECT,
                RuleStatus.ACTIVE
        );
        engine.register(limitRule);

        List<RuleResult> results = engine.evaluate(Map.of("amount", new BigDecimal("15000")));
        assertEquals(1, results.size());
        assertTrue(results.get(0).matched());
        assertEquals(RuleResult.Severity.BLOCKING, results.get(0).severity());

        assertThrows(RuleViolationException.class, () ->
                engine.enforce(Map.of("amount", new BigDecimal("15000"))));
    }

    @Test
    void allows_payment_below_threshold() {
        var engine = new DeclarativeRuleEngine();
        var limitRule = new RuleDefinition(
                RuleId.generate(),
                RuleCode.of("PAYMENT_LIMIT"),
                "High value payment check",
                "amount",
                ">",
                new BigDecimal("10000"),
                RuleResult.Severity.BLOCKING,
                RuleResult.RuleAction.REJECT,
                RuleStatus.ACTIVE
        );
        engine.register(limitRule);

        List<RuleResult> results = engine.evaluate(Map.of("amount", new BigDecimal("5000")));
        assertEquals(1, results.size());
        assertFalse(results.get(0).matched());

        assertDoesNotThrow(() -> engine.enforce(Map.of("amount", new BigDecimal("5000"))));
    }

    @Test
    void skips_inactive_rules() {
        var engine = new DeclarativeRuleEngine();
        var draftRule = new RuleDefinition(
                RuleId.generate(),
                RuleCode.of("DRAFT_RULE"),
                "Draft Rule",
                "amount",
                ">",
                new BigDecimal("100"),
                RuleResult.Severity.BLOCKING,
                RuleResult.RuleAction.REJECT,
                RuleStatus.DRAFT
        );
        engine.register(draftRule);

        List<RuleResult> results = engine.evaluate(Map.of("amount", new BigDecimal("500")));
        assertTrue(results.isEmpty());
    }
}
