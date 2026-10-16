package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.math.BigDecimal;

public final class MinimumQuantityConditionFactory
        implements CompiledConditionFactory {

    private static final ConditionType TYPE = ConditionType.of("minimum_quantity");

    @Override
    public ConditionType type() {
        return TYPE;
    }

    @Override
    public CompiledCondition compile(ConditionDefinition definition) {
        BigDecimal quantity = switch (definition.value()) {
            case BigDecimal bd -> bd;
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            case String s -> new BigDecimal(s);
            case null -> throw new PromotionCompilationException(
                    "minimum_quantity requires a numeric value");
            default -> throw new PromotionCompilationException(
                    "minimum_quantity requires a numeric value");
        };
        return new CompiledMinimumQuantityCondition(definition.operator(), quantity);
    }
}
