package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledEffect;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;

/** Metadata + compilation for one effect (product03.md §67). */
public interface PromotionEffectCapability {

    PromotionEffectDescriptor descriptor();

    default EffectType type() {
        return descriptor().type();
    }

    default String version() {
        return descriptor().version();
    }

    CompiledEffect compile(EffectDefinition definition);
}