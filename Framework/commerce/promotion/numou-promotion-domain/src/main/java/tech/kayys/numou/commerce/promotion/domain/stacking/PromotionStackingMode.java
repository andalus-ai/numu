package tech.kayys.syirkah.commerce.promotion.domain.stacking;

/**
 * Policy hint for how a promotion interacts with other promotions
 * after each has been evaluated independently (product03.md).
 */
public enum PromotionStackingMode {

    /** Compatible promotions may all apply. */
    STACK,

    /** If selected, competing promotions are excluded. */
    EXCLUSIVE,

    /** Choose the best monetary outcome among alternatives. */
    BEST_RESULT
}
