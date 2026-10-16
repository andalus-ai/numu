package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionOperator;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;

import java.util.Set;

/**
 * Self-describing metadata for a condition capability (product03.md §62).
 *
 * <p>Split from the effect/target descriptors on purpose: a condition declares
 * the operators and value type it understands, which is exactly what an admin
 * UI, a validator and a docs generator need — and nothing else.</p>
 */
public interface PromotionConditionDescriptor {

    ConditionType type();

    String version();

    PromotionCapabilityKind kind();

    Set<ConditionOperator> supportedOperators();

    PromotionValueType valueType();

    PromotionConditionParameterSchema schema();
}