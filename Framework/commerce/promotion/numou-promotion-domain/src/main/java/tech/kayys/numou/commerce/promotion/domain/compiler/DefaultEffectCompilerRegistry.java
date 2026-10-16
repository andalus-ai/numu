package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityRegistry;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilities;

import java.util.Objects;

/**
 * Compiled effect factory lookup built on the capability registry
 * (product03.md).
 *
 * <p>Delegating to the registry means the compiler never duplicates the
 * capability list: the registry is the single source of truth for what
 * effects can be compiled and how their parameters must be structured. The
 * static {@code defaults()} factory composes the registry from the shipped
 * capabilities, so callers that want the capability-driven path simply write
 * {@code new DefaultEffectCompilerRegistry(PromotionCapabilities.defaults())}.</p>
 */
public final class DefaultEffectCompilerRegistry implements EffectCompilerRegistry {

    private final PromotionCapabilityRegistry registry;

    public DefaultEffectCompilerRegistry(PromotionCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public static DefaultEffectCompilerRegistry defaults() {
        return new DefaultEffectCompilerRegistry(PromotionCapabilities.defaults());
    }

    @Override
    public CompiledEffectFactory require(EffectType type) {
        return new CapabilityEffectFactory(registry.requireEffect(type));
    }
}
