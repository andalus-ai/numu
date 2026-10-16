package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionCapability;

/**
 * Capability descriptor adapter for {@link CompiledConditionFactory}.
 *
 * <p>The capability registry is the single source of truth for registered
 * capabilities; this adapter turns a resolved capability into the
 * {@code ConditionType}-keyed factory contract used by the compiler
 * registries. Keeping the adapter here lets the capability registry stay the
 * only place where capability registration and versioning are defined.</p>
 */
public record CapabilityConditionFactory(PromotionConditionCapability capability)
        implements CompiledConditionFactory {

    @Override
    public ConditionType type() {
        return capability.type();
    }

    @Override
    public CompiledCondition compile(ConditionDefinition definition) {
        return capability.compile(definition);
    }
}