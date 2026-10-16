package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.promotion.domain.context.ChannelId;
import tech.kayys.syirkah.commerce.promotion.domain.context.CartSnapshot;
import tech.kayys.syirkah.commerce.promotion.domain.context.DefaultPromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineId;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionLineSnapshot;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionCompilerRegistryTest {

    @Test
    void compilesChannelAndMinimumQuantity() {
        var registry = DefaultConditionCompilerRegistry.defaults();

        CompiledCondition channel = registry.require(ConditionType.of("channel"))
                .compile(ConditionDefinition.leaf(
                        ConditionType.of("channel"),
                        ConditionOperator.EQUALS,
                        "POS"));

        CompiledCondition qty = registry.require(ConditionType.of("minimum_quantity"))
                .compile(ConditionDefinition.leaf(
                        ConditionType.of("minimum_quantity"),
                        ConditionOperator.GREATER_THAN_OR_EQUAL,
                        2));

        Money price = Money.of(10000, "IDR");
        var context = new DefaultPromotionEvaluationContext(
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

        assertTrue(channel.evaluate(context));
        assertTrue(qty.evaluate(context));

        var other = new DefaultPromotionEvaluationContext(
                context.effectiveAt(),
                ChannelId.of("ONLINE"),
                context.customer(),
                context.cart(),
                context.lines(),
                context.currentLineId());
        assertFalse(channel.evaluate(other));
    }
}
