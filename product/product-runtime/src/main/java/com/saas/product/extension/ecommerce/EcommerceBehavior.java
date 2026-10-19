package com.saas.product.extension.ecommerce;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.Money;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.spi.ProductBehavior;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

/**
 * E-commerce pricing behavior.
 *
 * Pricing chain:
 *   1. Resolve base unit price (tier pricing if applicable)
 *   2. Apply segment discounts (vip, wholesale)
 *   3. Apply coupon codes (placeholder — real impl delegates to PromotionService)
 *   4. Calculate tax (placeholder — real impl delegates to TaxService)
 *   5. Return auditable PricingResult
 */
@ApplicationScoped
public class EcommerceBehavior implements ProductBehavior {

    // Discount rates by customer segment
    private static final BigDecimal VIP_DISCOUNT       = new BigDecimal("0.10"); // 10%
    private static final BigDecimal WHOLESALE_DISCOUNT = new BigDecimal("0.20"); // 20%
    private static final BigDecimal DEFAULT_TAX_RATE   = new BigDecimal("0.11"); // 11% (Indonesia PPN)

    @Override
    public String getContext() {
        return EcommerceExtension.CONTEXT;
    }

    @Override
    public PricingResult calculatePrice(ProductAggregate product, PricingContext ctx) {
        EcommerceExtension ext = product.requireExtension(EcommerceExtension.CONTEXT);

        int quantity   = ctx.getQuantity();
        String segment = ctx.getCustomerSegment();
        String channel = ctx.getChannelId();

        // Step 1: tier price
        Money unitPrice = ext.priceForQuantity(quantity);

        PricingResult.Builder result = PricingResult.builder(unitPrice)
                .quantity(quantity)
                .line("Base price", unitPrice, "BASE");

        // Step 2: segment discount
        BigDecimal discountRate = switch (segment) {
            case "vip"       -> VIP_DISCOUNT;
            case "wholesale" -> WHOLESALE_DISCOUNT;
            default          -> BigDecimal.ZERO;
        };

        Money discountAmount = Money.zero(unitPrice.getCurrencyCode());
        if (discountRate.compareTo(BigDecimal.ZERO) > 0) {
            discountAmount = unitPrice.percentage(discountRate.multiply(BigDecimal.valueOf(100)));
            result.discount(discountAmount)
                  .line(segment + " discount (" + discountRate.multiply(BigDecimal.valueOf(100)).toPlainString() + "%)",
                          discountAmount.multiply(-1), "DISCOUNT");
        }

        // Step 3: coupon (simplified — delegate to PromotionService in real impl)
        // ctx.getAppliedCouponCodes() → call PromotionService here

        // Step 4: tax (simplified — delegate to TaxService in real impl)
        // "pos" channel is typically tax-inclusive; online is tax-exclusive
        Money netPrice = unitPrice.subtract(discountAmount);
        Money taxAmount;
        if ("pos".equals(channel)) {
            // Tax-inclusive: extract tax from price
            taxAmount = netPrice.percentage(DEFAULT_TAX_RATE.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.ONE.add(DEFAULT_TAX_RATE), 4, java.math.RoundingMode.HALF_UP));
        } else {
            // Tax-exclusive: add tax on top
            taxAmount = netPrice.percentage(DEFAULT_TAX_RATE.multiply(BigDecimal.valueOf(100)));
            result.line("Tax (" + DEFAULT_TAX_RATE.multiply(BigDecimal.valueOf(100)).toPlainString() + "%)",
                    taxAmount, "TAX");
        }

        return result.tax(taxAmount).build();
    }
}
