package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionRule;
import tech.kayys.syirkah.commerce.promotion.domain.action.FixedAmountOff;
import tech.kayys.syirkah.commerce.promotion.domain.action.PromotionAction;
import tech.kayys.syirkah.commerce.promotion.domain.action.PercentOff;
import tech.kayys.syirkah.commerce.promotion.domain.condition.MinimumQuantityOf;
import tech.kayys.syirkah.commerce.promotion.domain.condition.PromotionCondition;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityRegistry;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionCapability;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionEffectCapability;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Compiled rule ready for evaluation: conditions, targets and effects are
 * already typed runtime objects produced by the capability registry
 * (product03.md).
 *
 * <p>The persisted {@link PromotionRule} is a dynamic blueprint (it can carry
 * any condition/action type); the compiler converts it into this strongly typed
 * form once so that evaluation is fast and free of reflection or {@code Object}
 * casting. Unsupported legacy condition/action types are wrapped as compiled
 * delegates, so every promotion compiles while registry-backed types get the
 * typed, validated execution path.</p>
 */
public record CompiledPromotionRule(
        int priority,
        CompiledCondition condition,
        CompiledTarget target,
        CompiledEffect effect
) {

    public CompiledPromotionRule {
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(effect, "effect cannot be null");
    }

    /**
     * Compiles a persisted rule into typed runtime pieces.
     *
     * @param rule      the persisted rule (raw condition/action)
     * @param registry  the capability registry that owns registered condition
     *                  and effect capabilities
     * @return a compiled rule whose condition/effect are typed compiled objects
     */
    public static CompiledPromotionRule compile(
            PromotionRule rule,
            PromotionCapabilityRegistry registry) {

        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(registry, "registry cannot be null");

        int priority = rule.priority();
        CompiledCondition condition = compileCondition(rule.condition(), registry);
        CompiledEffect effect = compileEffect(rule.action(), registry);
        // The current persisted model does not attach a target to a rule; the
        // effect is therefore applied to the whole cart (product03 default).
        CompiledTarget target = (context) -> new SingleTargetSelection(CartTarget.INSTANCE);

        return new CompiledPromotionRule(priority, condition, target, effect);
    }

    private static CompiledCondition compileCondition(
            PromotionCondition condition,
            PromotionCapabilityRegistry registry) {

        // Known condition backed by a typed capability.
        if (condition instanceof MinimumQuantityOf minQty) {
            try {
                PromotionConditionCapability capability =
                        registry.requireCondition(ConditionType.of("minimum_quantity"));
                ConditionDefinition definition = new ConditionDefinition(
                        ConditionType.of("minimum_quantity"),
                        ConditionOperator.GREATER_THAN_OR_EQUAL,
                        minQty.minimum(),
                        Optional.empty(),
                        List.of(),
                        Map.of());
                return capability.compile(definition);
            } catch (PromotionCompilationException ignored) {
                // Not registered -> fall back to the raw delegate below.
            }
        }
        // Any condition the registry cannot compile stays a raw delegate,
        // preserving its original matches() semantics (e.g. line refs).
        return new RawPromotionCondition(condition);
    }

    private static CompiledEffect compileEffect(
            PromotionAction action,
            PromotionCapabilityRegistry registry) {

        // Known effect backed by a typed, parameter-validated capability.
        if (action instanceof PercentOff percentOff) {
            try {
                PromotionEffectCapability capability =
                        registry.requireEffect(EffectType.of("percentage_discount"));
                EffectDefinition definition = new EffectDefinition(
                        EffectType.of("percentage_discount"),
                        Map.of("percentage", percentOff.percentage().value()));
                return capability.compile(definition);
            } catch (PromotionCompilationException ignored) {
                // Not registered -> fall back to the raw delegate below.
            }
        }
        if (action instanceof FixedAmountOff fixed) {
            try {
                PromotionEffectCapability capability =
                        registry.requireEffect(EffectType.of("fixed_discount"));
                EffectDefinition definition = new EffectDefinition(
                        EffectType.of("fixed_discount"),
                        Map.of("amount", fixed.amount().amount(),
                               "currency", fixed.amount().currency().code()));
                return capability.compile(definition);
            } catch (PromotionCompilationException ignored) {
                // Not registered -> fall back to the raw delegate below.
            }
        }
        // Any effect the registry cannot compile stays a raw delegate,
        // preserving its original discountFor() semantics (e.g. free units,
        // bundle price).
        return new RawPromotionEffect(action);
    }
}
