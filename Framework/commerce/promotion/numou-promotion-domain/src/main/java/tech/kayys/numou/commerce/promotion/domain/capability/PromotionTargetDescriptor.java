package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.TargetType;

/**
 * Self-describing metadata for a target capability (product03.md §63-§66).
 *
 * <p>Target descriptors are intentionally minimal: targets have no comparison
 * operators and usually no parameters beyond the referenced identity.</p>
 */
public interface PromotionTargetDescriptor {

    TargetType type();

    String version();

    PromotionCapabilityKind kind();

    PromotionParameterSchema parameterSchema();
}