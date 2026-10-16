package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.util.Objects;

public record CompiledChannelCondition(
        ConditionOperator operator,
        String expectedChannel
) implements CompiledCondition {

    public CompiledChannelCondition {
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(expectedChannel, "expectedChannel cannot be null");
    }

    @Override
    public boolean evaluate(PromotionEvaluationContext context) {
        String actual = context.channel().value();
        return switch (operator) {
            case EQUALS -> actual.equals(expectedChannel);
            case NOT_EQUALS -> !actual.equals(expectedChannel);
            default -> throw new IllegalStateException(
                    "Invalid compiled channel operator: " + operator);
        };
    }
}
