package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledCondition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;

/**
 * Metadata + compilation for one condition (product03.md §67).
 *
 * <p>Keeping descriptor and compiler together means a capability can never be
 * registered without its own validation contract, and lookup by
 * {@code type + version} stays deterministic.</p>
 */
public interface PromotionConditionCapability {

    PromotionConditionDescriptor descriptor();

    default ConditionType type() {
        return descriptor().type();
    }

    default String version() {
        return descriptor().version();
    }

    CompiledCondition compile(ConditionDefinition definition);
}