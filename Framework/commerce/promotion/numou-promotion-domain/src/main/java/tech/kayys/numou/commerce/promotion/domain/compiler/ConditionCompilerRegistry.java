package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface ConditionCompilerRegistry {

    CompiledConditionFactory require(ConditionType type);
}
