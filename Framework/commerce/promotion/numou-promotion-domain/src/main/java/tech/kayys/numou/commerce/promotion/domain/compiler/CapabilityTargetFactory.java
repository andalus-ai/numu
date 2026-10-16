package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionTargetCapability;

/**
 * Capability descriptor adapter for {@link CompiledTargetFactory}.
 *
 * <p>Turns a resolved target capability into the {@code TargetType}-keyed
 * factory contract used by the target compiler registry, delegating both the
 * {@code type()} identity and the {@code compile} semantics to the capability.</p>
 */
public record CapabilityTargetFactory(PromotionTargetCapability capability)
        implements CompiledTargetFactory {

    @Override
    public TargetType type() {
        return capability.type();
    }

    @Override
    public CompiledTarget compile(TargetDefinition definition) {
        return capability.compile(definition);
    }
}