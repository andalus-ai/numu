package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.action.BundlePrice;
import tech.kayys.syirkah.commerce.promotion.domain.action.FixedAmountOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.FreeUnits;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.AllLinesPresent;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumCartTotal;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilities;
import tech.kayys.syirkah.commerce.promotion.domain.context.CartSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.DefaultPromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineId;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CompiledPromotionRule (capability-driven compilation)")
class CompiledPromotionRuleTest {

    private final DefaultPromotionEvaluationContext context = context();

    private static DefaultPromotionEvaluationContext context() {
        Money price = Money.of(10000, "IDR");
        return new DefaultPromotionEvaluationContext(
                Instant.parse("2026-06-15T10:00:00Z"),
                ChannelId.of("POS"),
                Optional.empty(),
                new CartSnapshot(price, Money.zero(price.currency()), price),
                List.of(new PromotionLineSnapshot(
                        PromotionLineId.of("COFFEE"),
                        Optional.empty(),
                        Optional.empty(),
                        "COFFEE",
                        Quantity.of(3, Unit.of("pcs")),
                        price)),
                Optional.empty());
    }

    @Test
    void compilesPercentOffThroughCapabilityRegistry() {
        var rule = new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new PercentOff(Percentage.of(10)));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        // The registry-backed condition is a typed CompiledCondition (not the
        // adapter); the effect is a capability-driven CompiledEffect.
        assertTrue(compiled.condition().evaluate(context));
        assertTrue(compiled.effect().evaluate(
                tech.kayys.syirkah.commerce.promotion.domain.PromotionId.generate(),
                context,
                compiled.target().resolve(context)).size() == 1);
    }

    @Test
    void compilesFixedAmountOffThroughCapabilityRegistry() {
        var rule = new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FixedAmountOff(Money.of(5000, "IDR")));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        assertTrue(compiled.condition().evaluate(context));
        var benefits = compiled.effect().evaluate(
                tech.kayys.syirkah.commerce.promotion.domain.PromotionId.generate(),
                context,
                compiled.target().resolve(context));
        assertEquals(1, benefits.size());
        assertInstanceOf(FixedDiscountBenefit.class, benefits.getFirst());
    }

    @Test
    void compilesUnsupportedConditionAsRawDelegate() {
        var rule = new PromotionRule(
                new AllLinesPresent(List.of("COFFEE", "CROISSANT")),
                new PercentOff(Percentage.of(10)));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        assertInstanceOf(RawPromotionCondition.class, compiled.condition());
        // Cart only has COFFEE, so AllLinesPresent(COFFEE, CROISSANT) fails.
        assertFalse(compiled.condition().evaluate(context));
    }

    @Test
    void compilesUnsupportedEffectAsRawDelegate() {
        var rule = new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new FreeUnits("COFFEE", 1));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        assertInstanceOf(RawPromotionEffect.class, compiled.effect());
    }

    @Test
    void compilesMinimumCartTotalAsRawDelegate() {
        var rule = new PromotionRule(
                new MinimumCartTotal(Money.of(50000, "IDR")),
                new PercentOff(Percentage.of(10)));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        assertInstanceOf(RawPromotionCondition.class, compiled.condition());
    }

    @Test
    void compilesBundlePriceAsRawDelegate() {
        var rule = new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new BundlePrice(Money.of(40000, "IDR")));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        assertInstanceOf(RawPromotionEffect.class, compiled.effect());
    }

    @Test
    void targetDefaultsToCart() {
        var rule = new PromotionRule(
                new MinimumQuantityOf("COFFEE", 2),
                new PercentOff(Percentage.of(10)));
        var compiled = CompiledPromotionRule.compile(rule, PromotionCapabilities.defaults());

        var selection = compiled.target().resolve(context);
        assertInstanceOf(SingleTargetSelection.class, selection);
        assertEquals(CartTarget.INSTANCE, selection.primary());
    }
}
