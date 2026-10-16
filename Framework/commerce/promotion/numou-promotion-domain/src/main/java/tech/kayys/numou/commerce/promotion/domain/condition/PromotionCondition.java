package tech.kayys.syirkah.commerce.promotion.domain.condition;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;

/**
 * The "WHEN" half of a promotion rule (blueprint Phase F).
 * Pure predicate over a cart snapshot — no money math here;
 * that is the action's job.
 */
public interface PromotionCondition {

    boolean matches(PromotionContext cart);
}
