package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * Default stacking policy from product03.md — delegates to
 * {@link ConfigurablePromotionStackingPolicy}.
 */
public final class DefaultPromotionStackingPolicy implements PromotionStackingPolicy {

    private final ConfigurablePromotionStackingPolicy delegate;

    public DefaultPromotionStackingPolicy() {
        this(Money.of(0, "IDR"));
    }

    public DefaultPromotionStackingPolicy(Money originalAmount) {
        this.delegate = new ConfigurablePromotionStackingPolicy(
                originalAmount != null ? originalAmount : Money.of(0, "IDR"));
    }

    public DefaultPromotionStackingPolicy(
            Money originalAmount,
            PromotionBenefitCompatibilityPolicy compatibility
    ) {
        this.delegate = new ConfigurablePromotionStackingPolicy(
                Objects.requireNonNull(originalAmount), compatibility);
    }

    @Override
    public PromotionCompositionResult compose(List<PromotionEvaluation> evaluations) {
        return delegate.compose(evaluations);
    }

    @Override
    public PromotionCompositionResult compose(
            List<PromotionEvaluation> evaluations,
            PromotionEvaluationContext context
    ) {
        return delegate.compose(evaluations, context);
    }
}
