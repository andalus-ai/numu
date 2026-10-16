package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.CartTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.ChannelConditionDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.CurrentLineTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.FixedDiscountDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.MinimumQuantityConditionDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.PercentageDiscountDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.ProductOfferingTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.ProductTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor.SkuTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CartTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ChannelConditionFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledCondition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledConditionFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledEffect;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledEffectFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledTarget;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.CurrentLineTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.FixedDiscountEffectFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.MinimumQuantityConditionFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PercentageDiscountEffectFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ProductOfferingTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ProductTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.SkuTargetFactory;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Assembles capabilities by pairing a descriptor with the compiler factory that
 * owns the actual compilation semantics (product03.md §67-§68).
 *
 * <p>The factories stay the single source of truth for <em>how</em> a definition
 * compiles; the descriptor adds the self-describing metadata for validation,
 * documentation and the tenant-facing capability API. One registration therefore
 * yields descriptor + schema + compiler + runtime semantics together.</p>
 */
public final class PromotionCapabilities {

    private PromotionCapabilities() {
    }

    public static PromotionConditionCapability condition(
            PromotionConditionDescriptor descriptor, CompiledConditionFactory factory) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        return new PromotionConditionCapability() {
            @Override
            public PromotionConditionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public CompiledCondition compile(ConditionDefinition definition) {
                return factory.compile(definition);
            }
        };
    }

    public static PromotionTargetCapability target(
            PromotionTargetDescriptor descriptor, CompiledTargetFactory factory) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        return new PromotionTargetCapability() {
            @Override
            public PromotionTargetDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public CompiledTarget compile(TargetDefinition definition) {
                return factory.compile(definition);
            }
        };
    }

    public static PromotionEffectCapability effect(
            PromotionEffectDescriptor descriptor, CompiledEffectFactory factory) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        return new PromotionEffectCapability() {
            @Override
            public PromotionEffectDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public CompiledEffect compile(EffectDefinition definition) {
                return factory.compile(definition);
            }
        };
    }

    /** Registry over every capability shipped with the engine. */
    public static PromotionCapabilityRegistry defaults() {
        List<PromotionConditionCapability> conditions = List.of(
                condition(new ChannelConditionDescriptor(), new ChannelConditionFactory()),
                condition(new MinimumQuantityConditionDescriptor(),
                        new MinimumQuantityConditionFactory()));

        List<PromotionTargetCapability> targets = List.of(
                target(new CartTargetDescriptor(), new CartTargetFactory()),
                target(new CurrentLineTargetDescriptor(), new CurrentLineTargetFactory()),
                target(new ProductTargetDescriptor(), new ProductTargetFactory()),
                target(new SkuTargetDescriptor(), new SkuTargetFactory()),
                target(new ProductOfferingTargetDescriptor(), new ProductOfferingTargetFactory()));

        List<PromotionEffectCapability> effects = List.of(
                effect(new PercentageDiscountDescriptor(), new PercentageDiscountEffectFactory()),
                effect(new FixedDiscountDescriptor(), new FixedDiscountEffectFactory()));

        return new DefaultPromotionCapabilityRegistry(conditions, targets, effects);
    }
}