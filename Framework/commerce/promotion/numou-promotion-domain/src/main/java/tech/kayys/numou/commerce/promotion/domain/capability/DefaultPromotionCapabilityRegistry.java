package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionCompilationException;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Versioned capability registry (product03.md §70).
 *
 * <p>Each capability pairs a descriptor with the existing compiler factory, so
 * the factories remain the single source of truth for compilation semantics
 * while the descriptor layer supplies the self-describing metadata the admin
 * UI, validator and tenant-facing API need (§61, §71).</p>
 */
public final class DefaultPromotionCapabilityRegistry implements PromotionCapabilityRegistry {

    private final Map<PromotionCapabilityId, PromotionConditionCapability> conditions;
    private final Map<PromotionCapabilityId, PromotionTargetCapability> targets;
    private final Map<PromotionCapabilityId, PromotionEffectCapability> effects;

    public DefaultPromotionCapabilityRegistry(
            Collection<PromotionConditionCapability> conditionCapabilities,
            Collection<PromotionTargetCapability> targetCapabilities,
            Collection<PromotionEffectCapability> effectCapabilities) {
        Objects.requireNonNull(conditionCapabilities, "conditionCapabilities cannot be null");
        Objects.requireNonNull(targetCapabilities, "targetCapabilities cannot be null");
        Objects.requireNonNull(effectCapabilities, "effectCapabilities cannot be null");

        this.conditions = index(conditionCapabilities, cap ->
                new PromotionCapabilityId(cap.type().value(), cap.version()));
        this.targets = index(targetCapabilities, cap ->
                new PromotionCapabilityId(cap.type().value(), cap.version()));
        this.effects = index(effectCapabilities, cap ->
                new PromotionCapabilityId(cap.type().value(), cap.version()));
    }

    private static <C> Map<PromotionCapabilityId, C> index(
            Collection<C> capabilities, Function<C, PromotionCapabilityId> keyFn) {
        return capabilities.stream().collect(Collectors.toUnmodifiableMap(
                keyFn, Function.identity(), (a, b) -> a));
    }

    @Override
    public PromotionConditionCapability requireCondition(ConditionType type, String version) {
        PromotionConditionCapability capability = conditions.get(
                new PromotionCapabilityId(type.value(), version));
        if (capability == null) {
            throw new PromotionCompilationException(
                    "Unknown condition capability: " + type.value() + "@" + version);
        }
        return capability;
    }

    @Override
    public PromotionTargetCapability requireTarget(TargetType type, String version) {
        PromotionTargetCapability capability = targets.get(
                new PromotionCapabilityId(type.value(), version));
        if (capability == null) {
            throw new PromotionCompilationException(
                    "Unknown target capability: " + type.value() + "@" + version);
        }
        return capability;
    }

    @Override
    public PromotionEffectCapability requireEffect(EffectType type, String version) {
        PromotionEffectCapability capability = effects.get(
                new PromotionCapabilityId(type.value(), version));
        if (capability == null) {
            throw new PromotionCompilationException(
                    "Unknown effect capability: " + type.value() + "@" + version);
        }
        return capability;
    }
    @Override

    public PromotionConditionCapability requireCondition(ConditionType type) {
        return latest(conditions(), type, PromotionConditionCapability::type,
                PromotionConditionCapability::version)
                .orElseThrow(() -> PromotionCompilationException.of(
                        "UNKNOWN_CONDITION",
                        "No registered condition capability: " + type.value(),
                        "condition"));
    }

    @Override
    public PromotionTargetCapability requireTarget(TargetType type) {
        return latest(targets(), type, PromotionTargetCapability::type,
                PromotionTargetCapability::version)
                .orElseThrow(() -> PromotionCompilationException.of(
                        "UNKNOWN_TARGET",
                        "No registered target capability: " + type.value(),
                        "target"));
    }

    @Override
    public PromotionEffectCapability requireEffect(EffectType type) {
        return latest(effects(), type, PromotionEffectCapability::type,
                PromotionEffectCapability::version)
                .orElseThrow(() -> PromotionCompilationException.of(
                        "UNKNOWN_EFFECT",
                        "No registered effect capability: " + type.value(),
                        "effect"));
    }

    private static <C> Optional<C> latest(
            Collection<C> candidates, Object type,
            Function<C, Object> typeFn, Function<C, String> versionFn) {
        return candidates.stream()
                .filter(cap -> typeFn.apply(cap).equals(type))
                .max(Comparator.comparingInt(
                        cap -> DefaultPromotionCapabilityRegistry.versionRank(versionFn.apply(cap))));
    }

    private static int versionRank(String version) {
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return Integer.compare(version.compareTo("1"), 0);
        }
    }

    @Override
    public Collection<PromotionConditionCapability> conditions() {
        return conditions.values();
    }

    @Override
    public Collection<PromotionTargetCapability> targets() {
        return targets.values();
    }

    @Override
    public Collection<PromotionEffectCapability> effects() {
        return effects.values();
    }
}