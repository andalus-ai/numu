package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.action.PromotionAction;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContexts;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTargetSelection;
import tech.kayys.syirkah.commerce.promotion.domain.target.SingleTargetSelection;

import java.util.List;

/**
 * Compiled effect that delegates to a legacy raw {@link PromotionAction}.
 *
 * <p>When a persisted promotion uses an effect type that the capability
 * registry cannot compile (for example {@code FreeUnits} or {@code BundlePrice}),
 * compilation still succeeds by bridging the raw {@link PromotionAction} into the
 * typed {@link CompiledEffect} boundary. The discount is captured as a
 * {@link FixedDiscountBenefit} on the cart target, matching the legacy
 * behaviour of valuing the action against the whole cart.</p>
 */
public final class RawPromotionEffect implements CompiledEffect {

    private final PromotionAction action;

    public RawPromotionEffect(PromotionAction action) {
        this.action = action;
    }

    @Override
    public List<PromotionBenefit> evaluate(
            PromotionId promotionId,
            PromotionEvaluationContext context,
            PromotionTargetSelection targets) {

        PromotionContext cart = PromotionEvaluationContexts.toPromotionContext(context);
        var discount = action.discountFor(cart);
        if (discount.isZero()) {
            return List.of();
        }
        PromotionTargetSelection selection;
        if (targets != null && !targets.targets().isEmpty()) {
            selection = targets;
        } else {
            selection = new SingleTargetSelection(CartTarget.INSTANCE);
        }
        return List.of(new FixedDiscountBenefit(
                promotionId,
                selection.primary(),
                discount,
                "raw effect [" + action.getClass().getSimpleName() + "]"));
    }
}
