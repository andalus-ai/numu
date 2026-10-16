package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.math.BigDecimal;
import java.util.List;

public final class PercentageDiscountEffectFactory implements CompiledEffectFactory {

    private static final EffectType TYPE = EffectType.of("percentage_discount");

    @Override
    public EffectType type() {
        return TYPE;
    }

    @Override
    public CompiledEffect compile(EffectDefinition definition) {
        BigDecimal percentage = ParameterReader.decimal(definition.parameters(), "percentage");
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PromotionCompilationException(
                    "percentage must be between 0 and 100");
        }
        Percentage pct = Percentage.of(percentage);
        return (promotionId, context, targets) -> List.of(
                new PercentageDiscountBenefit(
                        promotionId,
                        targets.primary(),
                        pct,
                        "percentage_discount " + percentage));
    }
}
