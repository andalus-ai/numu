package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface CompiledConditionFactory {

    ConditionType type();

    CompiledCondition compile(ConditionDefinition definition);
}
