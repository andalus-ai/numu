package tech.kayys.syirkah.commerce.promotion.domain.validation;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.observability.PromotionEvaluationTracer;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionApplied;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingConfiguration;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromotionValidatorTest {

    private static Promotion promotion() {
        return Promotion.draft(
                PromotionId.generate(),
                "BUY2-10",
                1,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                new PromotionRule(
                        new MinimumQuantityOf("COFFEE", 2),
                        new PercentOff(Percentage.of(10))),
                PromotionStackingConfiguration.bestResult(1));
    }

    @Test
    void validPromotionPasses() {
        var result = new PromotionValidator().validate(promotion());
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void duplicateRuleIdsRejected() {
        var base = promotion();
        var rule = base.rules().getFirst();
        var duplicated = base.addRule(rule);
        var result = new PromotionValidator().validate(duplicated);
        assertFalse(result.valid());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.code().equals("RULE_DUPLICATE_ID")));
    }

    @Test
    void tracerExplainsAppliedAndSkipped() {
        var promo = promotion().activate();
        var benefit = new PercentageDiscountBenefit(
                promo.id(), CartTarget.INSTANCE, Percentage.of(10), "10% off");
        var applied = new PromotionApplied(
                promo.id(), promo.name(), promo.priority(), List.of(benefit));
        var traces = PromotionEvaluationTracer.trace(
                List.of(promo.id()), List.of(applied), List.of());
        assertEquals(1, traces.size());
        assertEquals("accepted", traces.getFirst().stacking());

        var skipped = PromotionEvaluationTracer.trace(
                List.of(promo.id()), List.of(), List.of());
        assertEquals("skipped", skipped.getFirst().stacking());
    }
}
