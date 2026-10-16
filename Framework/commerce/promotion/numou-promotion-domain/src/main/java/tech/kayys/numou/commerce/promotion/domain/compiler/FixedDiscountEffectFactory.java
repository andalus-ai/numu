package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.List;

public final class FixedDiscountEffectFactory implements CompiledEffectFactory {

    private static final EffectType TYPE = EffectType.of("fixed_discount");

    @Override
    public EffectType type() {
        return TYPE;
    }

    @Override
    public CompiledEffect compile(EffectDefinition definition) {
        BigDecimal amount = ParameterReader.decimal(definition.parameters(), "amount");
        String currency = ParameterReader.string(definition.parameters(), "currency");
        if (amount.signum() < 0) {
            throw new PromotionCompilationException(
                    "discount amount must not be negative");
        }
        Money money = Money.of(amount, currency);
        return (promotionId, context, targets) -> List.of(
                new FixedDiscountBenefit(
                        promotionId,
                        targets.primary(),
                        money,
                        "fixed_discount " + money));
    }
}
