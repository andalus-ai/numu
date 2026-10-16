package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionEffectCapability;

/**
 * Capability descriptor adapter for {@link CompiledEffectFactory}.
 *
 * <p>Turns a resolved effect capability into the {@code EffectType}-keyed
 * factory contract used by the effect compiler registry, delegating both the
 * {@code type()} identity and the {@code compile} semantics to the capability.</p>
 */
public record CapabilityEffectFactory(PromotionEffectCapability capability)
        implements CompiledEffectFactory {

    @Override
    public EffectType type() {
        return capability.type();
    }

    @Override
    public CompiledEffect compile(EffectDefinition definition) {
        return capability.compile(definition);
    }
}