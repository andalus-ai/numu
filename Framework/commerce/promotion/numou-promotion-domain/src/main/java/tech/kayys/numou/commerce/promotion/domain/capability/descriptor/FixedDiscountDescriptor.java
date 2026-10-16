package tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor;

import tech.kayys.syirkah.commerce.promotion.domain.capability.ParameterDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityKind;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionEffectDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionParameterSchema;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionValueType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;

import java.math.BigDecimal;

/**
 * Descriptor for the {@code fixed_discount} effect capability
 * (product03.md §63, §64).
 */
public final class FixedDiscountDescriptor implements PromotionEffectDescriptor {

    @Override
    public EffectType type() {
        return EffectType.of("fixed_discount");
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public PromotionCapabilityKind kind() {
        return PromotionCapabilityKind.EFFECT;
    }

    @Override
    public PromotionParameterSchema parameterSchema() {
        return PromotionParameterSchema.of(
                new ParameterDefinition("amount", PromotionValueType.NUMBER, true, BigDecimal.ZERO, null),
                new ParameterDefinition("currency", PromotionValueType.STRING, true, null, null));
    }
}