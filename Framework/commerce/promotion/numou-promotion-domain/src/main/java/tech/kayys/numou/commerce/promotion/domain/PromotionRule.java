package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.promotion.domain.action.PromotionAction;
import tech.kayys.syirkah.commerce.promotion.domain.condition.PromotionCondition;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * "BUY 2 COFFEE GET 10% OFF" as one unit: a condition plus an action
 * (blueprint Phase F / product03.md multi-rule).
 */
public record PromotionRule(
        PromotionRuleId id,
        PromotionCondition condition,
        PromotionAction action,
        int priority
) {

    public PromotionRule {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
    }

    /** Backward-compatible factory: generated id, priority 0. */
    public PromotionRule(PromotionCondition condition, PromotionAction action) {
        this(PromotionRuleId.generate(), condition, action, 0);
    }

    public PromotionRule withPriority(int priority) {
        return new PromotionRule(id, condition, action, priority);
    }

    public Money discount(PromotionContext cart) {
        return action.discountFor(cart);
    }

    public boolean applies(PromotionContext cart) {
        return condition.matches(cart) && discount(cart).isPositive();
    }
}
