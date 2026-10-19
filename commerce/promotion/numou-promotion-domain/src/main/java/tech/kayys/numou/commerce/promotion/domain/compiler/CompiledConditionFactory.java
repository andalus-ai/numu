package tech.kayys.numou.commerce.promotion.domain.compiler;

public interface CompiledConditionFactory {

    ConditionType type();

    CompiledCondition compile(ConditionDefinition definition);
}
