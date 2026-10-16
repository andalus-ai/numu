package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

import java.util.Collection;

/**
 * Versioned capability lookup (product03.md §70).
 *
 * <p>Lookup is {@code type + version}, never type alone, so a compiled
 * representation is deterministic and a capability upgrade cannot silently
 * reinterpret a persisted promotion (§69).</p>
 */
public interface PromotionCapabilityRegistry {

    PromotionConditionCapability requireCondition(ConditionType type, String version);

    PromotionTargetCapability requireTarget(TargetType type, String version);

    PromotionEffectCapability requireEffect(EffectType type, String version);

    /**
     * Latest-available version of the condition capability. Equivalent to
     * {@code requireCondition(type, latestVersion(type))}.
     */
    PromotionConditionCapability requireCondition(ConditionType type);

    /**
     * Latest-available version of the target capability. Equivalent to
     * {@code requireTarget(type, latestVersion(type))}.
     */
    PromotionTargetCapability requireTarget(TargetType type);

    /**
     * Latest-available version of the effect capability. Equivalent to
     * {@code requireEffect(type, latestVersion(type))}.
     */
    PromotionEffectCapability requireEffect(EffectType type);

    Collection<PromotionConditionCapability> conditions();

    Collection<PromotionTargetCapability> targets();

    Collection<PromotionEffectCapability> effects();
}