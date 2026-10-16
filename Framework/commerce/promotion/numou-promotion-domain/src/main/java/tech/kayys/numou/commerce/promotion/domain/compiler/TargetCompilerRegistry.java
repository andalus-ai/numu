package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface TargetCompilerRegistry {

    CompiledTargetFactory require(TargetType type);
}
