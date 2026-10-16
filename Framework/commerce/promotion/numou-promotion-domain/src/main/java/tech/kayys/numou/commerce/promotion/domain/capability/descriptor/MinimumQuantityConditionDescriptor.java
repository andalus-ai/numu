package tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityKind;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionParameterSchema;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionValueType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionOperator;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;

import java.util.Set;

/**
 * Descriptor for the {@code minimum_quantity} condition capability
 * (product03.md §62). Compares a number, so only magnitude operators apply.
 */
public final class MinimumQuantityConditionDescriptor implements PromotionConditionDescriptor {

    @Override
    public ConditionType type() {
        return ConditionType.of("minimum_quantity");
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public PromotionCapabilityKind kind() {
        return PromotionCapabilityKind.CONDITION;
    }

    @Override
    public Set<ConditionOperator> supportedOperators() {
        return Set.of(
                ConditionOperator.EQUALS,
                ConditionOperator.GREATER_THAN,
                ConditionOperator.GREATER_THAN_OR_EQUAL,
                ConditionOperator.LESS_THAN,
                ConditionOperator.LESS_THAN_OR_EQUAL);
    }

    @Override
    public PromotionValueType valueType() {
        return PromotionValueType.NUMBER;
    }

    @Override
    public PromotionConditionParameterSchema schema() {
        return PromotionConditionParameterSchema.none();
    }
}