package tech.kayys.numou.commerce.promotion.domain.compiler;

public interface TargetCompilerRegistry {

    CompiledTargetFactory require(TargetType type);
}
