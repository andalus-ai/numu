package tech.kayys.syirkah.commerce.promotion.domain.compiler;

public interface CompiledEffectFactory {

    EffectType type();

    CompiledEffect compile(EffectDefinition definition);
}
