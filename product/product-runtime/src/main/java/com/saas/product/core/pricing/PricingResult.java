package com.saas.product.core.pricing;

import com.saas.product.core.model.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a pricing computation.
 *
 * Carries a full audit trail of how the final price was derived
 * (base price → discounts → taxes → final price).
 */
public final class PricingResult {

    private final Money basePrice;           // per-unit price before discounts/taxes
    private final Money discountAmount;      // total discount applied
    private final Money taxAmount;           // total tax applied
    private final Money finalUnitPrice;      // base - discount + tax (per unit)
    private final Money finalTotalPrice;     // finalUnit × quantity
    private final int quantity;
    private final String currencyCode;
    private final List<PricingLineItem> lineItems;  // audit trail

    private PricingResult(Builder b) {
        this.basePrice       = Objects.requireNonNull(b.basePrice, "basePrice");
        this.discountAmount  = b.discountAmount != null ? b.discountAmount : Money.zero(b.basePrice.getCurrencyCode());
        this.taxAmount       = b.taxAmount != null ? b.taxAmount : Money.zero(b.basePrice.getCurrencyCode());
        this.quantity        = b.quantity > 0 ? b.quantity : 1;
        this.currencyCode    = b.basePrice.getCurrencyCode();
        this.lineItems       = b.lineItems != null ? Collections.unmodifiableList(b.lineItems) : List.of();

        // compute derived
        BigDecimal net = basePrice.getAmount()
                .subtract(discountAmount.getAmount())
                .add(taxAmount.getAmount());
        this.finalUnitPrice  = Money.of(net.max(BigDecimal.ZERO), currencyCode);
        this.finalTotalPrice = finalUnitPrice.multiply(quantity);
    }

    public static Builder builder(Money basePrice) {
        return new Builder(basePrice);
    }

    // Convenience: simple flat price
    public static PricingResult flat(Money price, int qty) {
        return builder(price).quantity(qty).build();
    }

    public Money getBasePrice()        { return basePrice; }
    public Money getDiscountAmount()   { return discountAmount; }
    public Money getTaxAmount()        { return taxAmount; }
    public Money getFinalUnitPrice()   { return finalUnitPrice; }
    public Money getFinalTotalPrice()  { return finalTotalPrice; }
    public int getQuantity()           { return quantity; }
    public String getCurrencyCode()    { return currencyCode; }
    public List<PricingLineItem> getLineItems() { return lineItems; }

    public boolean hasDiscount() {
        return !discountAmount.isZero();
    }

    @Override
    public String toString() {
        return "PricingResult{base=" + basePrice + ", discount=" + discountAmount
                + ", tax=" + taxAmount + ", total=" + finalTotalPrice + "}";
    }

    /**
     * Audit line item explaining one step in the pricing computation.
     */
    public record PricingLineItem(String label, Money amount, String type) {
        // type: "BASE", "DISCOUNT", "TAX", "FEE", "ROUNDING"
    }

    public static final class Builder {
        private final Money basePrice;
        private Money discountAmount;
        private Money taxAmount;
        private int quantity = 1;
        private List<PricingLineItem> lineItems = new ArrayList<>();

        private Builder(Money basePrice) { this.basePrice = basePrice; }

        public Builder discount(Money d)     { this.discountAmount = d; return this; }
        public Builder tax(Money t)          { this.taxAmount = t; return this; }
        public Builder quantity(int q)       { this.quantity = q; return this; }
        public Builder line(String label, Money amount, String type) {
            lineItems.add(new PricingLineItem(label, amount, type));
            return this;
        }
        public PricingResult build()         { return new PricingResult(this); }
    }
}
