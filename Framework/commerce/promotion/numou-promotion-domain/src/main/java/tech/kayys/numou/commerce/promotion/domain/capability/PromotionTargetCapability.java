package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.CompiledTarget;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetDefinition;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

/** Metadata + compilation for one target (product03.md §67). */
public interface PromotionTargetCapability {

    PromotionTargetDescriptor descriptor();

    default TargetType type() {
        return descriptor().type();
    }

    default String version() {
        return descriptor().version();
    }

    CompiledTarget compile(TargetDefinition definition);
}