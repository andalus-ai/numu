package tech.kayys.numou.commerce.promotion.domain.compiler;

public interface EffectCompilerRegistry {

    CompiledEffectFactory require(EffectType type);
}
