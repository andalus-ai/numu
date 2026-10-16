package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.promotion.domain.composition.PromotionBenefitComposer;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContexts;
import tech.kayys.syirkah.commerce.promotion.domain.result.PromotionResult;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.DefaultPromotionStackingPolicy;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionCompositionResult;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionEvaluation;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionStackingPolicy;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Promotion evaluation pipeline (product03.md):
 * candidates → evaluate → stacking → compose → PromotionResult.
 */
public final class PromotionEngine {

    private final PromotionBenefitComposer composer;
    private final PromotionEvaluator evaluator;

    public PromotionEngine() {
        this(new PromotionBenefitComposer(), new DefaultPromotionEvaluator());
    }

    public PromotionEngine(PromotionBenefitComposer composer) {
        this(composer, new DefaultPromotionEvaluator());
    }

    public PromotionEngine(
            PromotionBenefitComposer composer,
            PromotionEvaluator evaluator
    ) {
        this.composer = Objects.requireNonNull(composer);
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    public PromotionResult evaluate(
            List<Promotion> candidates,
            PromotionEvaluationContext context
    ) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        Money original = context.cart().total();
        List<PromotionEvaluation> evaluations = new ArrayList<>();
        for (var promotion : candidates) {
            evaluator.evaluate(promotion, context).ifPresent(evaluations::add);
        }

        if (evaluations.isEmpty()) {
            return PromotionResult.none(original);
        }

        PromotionStackingPolicy stacking = new DefaultPromotionStackingPolicy(original);
        PromotionCompositionResult composition = stacking.compose(evaluations, context);
        var breakdown = composer.compose(original, composition.applied());
        return PromotionResult.of(
                original,
                composition.applied(),
                composition.rejected(),
                breakdown);
    }

    public PromotionResult evaluate(
            List<Promotion> candidates,
            PromotionContext cart,
            PriceResult cartPrice
    ) {
        Objects.requireNonNull(candidates, "candidates cannot be null");
        Objects.requireNonNull(cart, "cart cannot be null");
        Objects.requireNonNull(cartPrice, "cartPrice cannot be null");

        if (cartPrice.finalPrice().compareTo(cart.total()) != 0) {
            throw new IllegalArgumentException(
                    "cartPrice must resolve to the cart line total");
        }

        PromotionEvaluationContext context = PromotionEvaluationContexts.from(cart);
        PromotionResult result = evaluate(candidates, context);

        // Prefer cartPrice baseline when caller supplies priced cart
        if (result.applied().isEmpty()) {
            return PromotionResult.none(cartPrice.finalPrice());
        }

        PromotionStackingPolicy stacking =
                new DefaultPromotionStackingPolicy(cartPrice.finalPrice());
        List<PromotionEvaluation> evaluations = new ArrayList<>();
        for (var promotion : candidates) {
            evaluator.evaluate(promotion, context).ifPresent(evaluations::add);
        }
        PromotionCompositionResult composition = stacking.compose(evaluations, context);
        var breakdown = composer.compose(cartPrice.finalPrice(), composition.applied());
        return PromotionResult.of(
                cartPrice.finalPrice(),
                composition.applied(),
                composition.rejected(),
                breakdown);
    }

    public Optional<PromotionOutcome> apply(
            List<Promotion> candidates,
            PromotionContext cart,
            PriceResult cartPrice
    ) {
        PromotionResult result = evaluate(candidates, cart, cartPrice);
        if (!result.hasApplications()) {
            return Optional.empty();
        }

        var first = result.applied().getFirst();
        Promotion winner = candidates.stream()
                .filter(p -> p.id().equals(first.promotionId()))
                .findFirst()
                .orElseThrow();

        PriceResult discounted = composer.toPriceResult(cartPrice, result.price());
        return Optional.of(new PromotionOutcome(
                winner,
                first.benefits().getFirst().description(),
                result.price().totalDiscount(),
                cartPrice,
                discounted));
    }
}
