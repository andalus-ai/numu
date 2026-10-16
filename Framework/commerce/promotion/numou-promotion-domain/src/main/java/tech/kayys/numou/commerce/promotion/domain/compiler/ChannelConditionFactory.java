package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public final class ChannelConditionFactory implements CompiledConditionFactory {

    private static final ConditionType TYPE = ConditionType.of("channel");

    @Override
    public ConditionType type() {
        return TYPE;
    }

    @Override
    public CompiledCondition compile(ConditionDefinition definition) {
        if (definition.operator() != ConditionOperator.EQUALS
                && definition.operator() != ConditionOperator.NOT_EQUALS) {
            throw new PromotionCompilationException(
                    "channel supports only EQUALS and NOT_EQUALS");
        }
        if (!(definition.value() instanceof String value)) {
            throw new PromotionCompilationException(
                    "channel condition requires a string");
        }
        return new CompiledChannelCondition(definition.operator(), value);
    }
}
