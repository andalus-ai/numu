package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityRegistry;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilities;

import java.util.Objects;

/**
 * Compiled condition factory lookup built on the capability registry
 * (product03.md).
 *
 * <p>Delegating to the registry means the compiler never duplicates the
 * capability list: the registry is the single source of truth for what
 * conditions can be compiled and how their parameters must be structured. The
 * static {@code defaults()} factory composes the registry from the shipped
 * capabilities, so callers that want the capability-driven path simply write
 * {@code new DefaultConditionCompilerRegistry(PromotionCapabilities.defaults())}.</p>
 */
public final class DefaultConditionCompilerRegistry implements ConditionCompilerRegistry {

    private final PromotionCapabilityRegistry registry;

    public DefaultConditionCompilerRegistry(PromotionCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public static DefaultConditionCompilerRegistry defaults() {
        return new DefaultConditionCompilerRegistry(PromotionCapabilities.defaults());
    }

    @Override
    public CompiledConditionFactory require(ConditionType type) {
        return new CapabilityConditionFactory(registry.requireCondition(type));
    }
}
