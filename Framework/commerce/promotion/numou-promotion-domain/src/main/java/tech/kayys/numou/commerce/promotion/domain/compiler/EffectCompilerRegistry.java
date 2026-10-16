package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface EffectCompilerRegistry {

    CompiledEffectFactory require(EffectType type);
}
