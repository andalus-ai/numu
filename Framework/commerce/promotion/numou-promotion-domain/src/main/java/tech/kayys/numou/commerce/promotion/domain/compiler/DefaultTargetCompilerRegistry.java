package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityRegistry;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilities;

import java.util.Objects;

/**
 * Compiled target factory lookup built on the capability registry
 * (product03.md).
 *
 * <p>Delegating to the registry means the compiler never duplicates the
 * capability list: the registry is the single source of truth for what
 * targets can be compiled and what reference parameters each one requires. The
 * static {@code defaults()} factory composes the registry from the shipped
 * capabilities, so callers that want the capability-driven path simply write
 * {@code new DefaultTargetCompilerRegistry(PromotionCapabilities.defaults())}.</p>
 */
public final class DefaultTargetCompilerRegistry implements TargetCompilerRegistry {

    private final PromotionCapabilityRegistry registry;

    public DefaultTargetCompilerRegistry(PromotionCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public static DefaultTargetCompilerRegistry defaults() {
        return new DefaultTargetCompilerRegistry(PromotionCapabilities.defaults());
    }

    @Override
    public CompiledTargetFactory require(TargetType type) {
        return new CapabilityTargetFactory(registry.requireTarget(type));
    }
}
