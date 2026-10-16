package tech.kayys.syirkah.commerce.promotion.domain.capability.descriptor;

import tech.kayys.syirkah.commerce.promotion.domain.capability.ParameterDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionCapabilityKind;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionParameterSchema;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionTargetDescriptor;
import tech.kayys.syirkah.commerce.promotion.domain.capability.PromotionValueType;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

/** Descriptor for the {@code product} target capability (product02.md §18). */
public final class ProductTargetDescriptor implements PromotionTargetDescriptor {

    @Override
    public TargetType type() {
        return TargetType.of("product");
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
        return PromotionParameterSchema.of(
                new ParameterDefinition("productId", PromotionValueType.STRING, true, null, null));
    }
}