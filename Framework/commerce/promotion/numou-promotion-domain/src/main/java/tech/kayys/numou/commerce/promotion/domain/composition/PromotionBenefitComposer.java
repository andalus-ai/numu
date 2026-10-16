package tech.kayys.syirkah.commerce.promotion.domain.composition;

import tech.kayys.syirkah.commerce.pricing.domain.PriceAdjustment;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedPriceBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FreeItemBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.ShippingDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.result.PromotionPriceAdjustment;
import tech.kayys.syirkah.commerce.promotion.domain.result.PromotionPriceBreakdown;
import tech.kayys.syirkah.commerce.promotion.domain.stacking.PromotionApplied;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts mechanism-preserving benefits into monetary adjustments
 * (product03.md BenefitComposer).
 */
public final class PromotionBenefitComposer {

    private final MonetaryCompositionPolicy policy;

    public PromotionBenefitComposer() {
        this(MonetaryCompositionPolicy.sequential());
    }

    public PromotionBenefitComposer(MonetaryCompositionPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public PromotionPriceBreakdown compose(
            Money originalAmount,
            List<PromotionApplied> applied
    ) {
        Objects.requireNonNull(originalAmount, "originalAmount cannot be null");
        Objects.requireNonNull(applied, "applied cannot be null");

        List<PromotionPriceAdjustment> adjustments = new ArrayList<>();
        Money running = originalAmount;
        Money totalDiscount = Money.zero(originalAmount.currency());

        List<PercentageDiscountBenefit> percentages = new ArrayList<>();
        List<PromotionBenefit> others = new ArrayList<>();

        for (var promo : applied) {
            for (var benefit : promo.benefits()) {
                if (benefit instanceof PercentageDiscountBenefit pct) {
                    percentages.add(pct);
                } else {
                    others.add(benefit);
                }
            }
        }

        if (policy.percentageMode() == DiscountCompositionMode.ADDITIVE
                && !percentages.isEmpty()) {
            BigDecimal sum = percentages.stream()
                    .map(p -> p.percentage().value())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.valueOf(100)) > 0) {
                sum = BigDecimal.valueOf(100);
            }
            Money discount = originalAmount.multiply(
                    sum.divide(BigDecimal.valueOf(100)));
            if (discount.isPositive()) {
                adjustments.add(new PromotionPriceAdjustment(
                        percentages.getFirst().promotionId(),
                        CartTarget.INSTANCE,
                        discount));
                totalDiscount = totalDiscount.add(discount);
                running = running.subtract(discount);
            }
        } else {
            for (var pct : percentages) {
                Money discount = running.multiply(pct.percentage().factor());
                if (discount.isPositive()) {
                    adjustments.add(new PromotionPriceAdjustment(
                            pct.promotionId(),
                            pct.target(),
                            discount));
                    totalDiscount = totalDiscount.add(discount);
                    running = running.subtract(discount);
                }
            }
        }

        for (var benefit : others) {
            Money discount = monetaryValue(benefit, running);
            if (discount != null && discount.isPositive()) {
                if (discount.compareTo(running) > 0) {
                    discount = running;
                }
                adjustments.add(new PromotionPriceAdjustment(
                        benefit.promotionId(),
                        benefit.target(),
                        discount));
                totalDiscount = totalDiscount.add(discount);
                running = running.subtract(discount);
            }
        }

        return new PromotionPriceBreakdown(
                originalAmount,
                adjustments,
                running,
                totalDiscount);
    }

    /** Bridge to existing PriceResult chain. */
    public PriceResult toPriceResult(
            PriceResult original,
            PromotionPriceBreakdown breakdown
    ) {
        List<PriceAdjustment> list = new ArrayList<>(original.adjustments());
        for (var adj : breakdown.adjustments()) {
            list.add(new PriceAdjustment(
                    adj.amount().negate(),
                    "PROMO:" + adj.promotionId().value()));
        }
        return PriceResult.of(original.basePrice(), list);
    }

    private static Money monetaryValue(PromotionBenefit benefit, Money running) {
        return switch (benefit) {
            case FixedDiscountBenefit fixed -> fixed.amount();
            case FixedPriceBenefit fixedPrice -> {
                if (running.compareTo(fixedPrice.price()) > 0) {
                    yield running.subtract(fixedPrice.price());
                }
                yield Money.zero(running.currency());
            }
            case ShippingDiscountBenefit shipping -> shipping.amount();
            case FreeItemBenefit ignored -> Money.zero(running.currency());
            case PercentageDiscountBenefit ignored -> Money.zero(running.currency());
        };
    }
}
