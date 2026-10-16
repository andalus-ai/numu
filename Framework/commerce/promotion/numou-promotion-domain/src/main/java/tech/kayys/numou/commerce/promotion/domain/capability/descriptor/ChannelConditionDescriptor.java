package tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityKind;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionConditionParameterSchema;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionValueType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionOperator;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.ConditionType;

import java.util.Set;

/**
 * Descriptor for the {@code channel} condition capability (product03.md §62).
 *
 * <p>Channel is a string compared for equality only — the operators list is
 * intentionally closed so a tenant cannot declare {@code channel >= 5}.</p>
 */
public final class ChannelConditionDescriptor implements PromotionConditionDescriptor {

    @Override
    public ConditionType type() {
        return ConditionType.of("channel");
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
        return Set.of(ConditionOperator.EQUALS, ConditionOperator.NOT_EQUALS);
    }

    @Override
    public PromotionValueType valueType() {
        return PromotionValueType.STRING;
    }

    @Override
    public PromotionConditionParameterSchema schema() {
        return PromotionConditionParameterSchema.none();
    }
}