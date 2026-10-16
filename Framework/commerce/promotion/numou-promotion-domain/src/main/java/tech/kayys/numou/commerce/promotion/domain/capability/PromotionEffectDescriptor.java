package tech.kayys.syirkah.commerce.promotion.domain.capability;

import tech.kayys.syirkah.commerce.promotion.domain.compiler.EffectType;

/**
 * Self-describing metadata for an effect capability (product03.md §63).
 *
 * <p>Effects declare a parameter schema instead of operators: the schema is
 * what the compiler validates against and what the tenant-facing capability
 * API exposes (§71).</p>
 */
public interface PromotionEffectDescriptor {

    EffectType type();

    String version();

    PromotionCapabilityKind kind();

    PromotionParameterSchema parameterSchema();
}