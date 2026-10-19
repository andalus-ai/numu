package tech.kayys.numou.commerce.promotion.domain.compiler;

public interface ConditionCompilerRegistry {

    CompiledConditionFactory require(ConditionType type);
}
