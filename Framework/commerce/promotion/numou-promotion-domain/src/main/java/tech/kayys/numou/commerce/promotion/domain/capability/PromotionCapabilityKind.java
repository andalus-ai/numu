package tech.kayys.syirkah.commerce.promotion.domain.capability;

/**
 * Which flavour of promotion capability a descriptor describes
 * (product03.md §61.1).
 *
 * <p>Only used for generic listings — concrete lookups go through the split
 * {@code PromotionConditionDescriptor} / {@code PromotionTargetDescriptor} /
 * {@code PromotionEffectDescriptor} interfaces rather than one enormous
 * union interface.</p>
 */
public enum PromotionCapabilityKind {

    CONDITION,

    TARGET,

    EFFECT
}