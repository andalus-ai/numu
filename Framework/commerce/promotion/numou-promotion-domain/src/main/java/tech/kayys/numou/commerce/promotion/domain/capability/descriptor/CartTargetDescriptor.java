package tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor;

import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityKind;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionParameterSchema;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

/** Descriptor for the {@code cart} target capability (product03.md §66). */
public final class CartTargetDescriptor implements PromotionTargetDescriptor {

    @Override
    public TargetType type() {
        return TargetType.of("cart");
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public PromotionCapabilityKind kind() {
        return PromotionCapabilityKind.TARGET;
    }

    @Override
    public PromotionParameterSchema parameterSchema() {
        return PromotionParameterSchema.none();
    }
}