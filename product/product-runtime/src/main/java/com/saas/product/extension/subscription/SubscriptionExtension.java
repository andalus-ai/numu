package com.saas.product.extension.subscription;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.Money;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.spi.ProductBehavior;
import com.saas.product.spi.ProductExtension;
import com.saas.product.spi.ProductValidationException;
import com.saas.product.spi.ProductValidator;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// ─────────────────────────────────────────────────────────────────────────────
//  Extension
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Subscription / SaaS domain extension.
 *
 * Covers:
 *  - Billing interval (monthly, quarterly, yearly)
 *  - Free trial
 *  - Setup fee (one-time)
 *  - Seat-based pricing
 *  - Annual discount (pay yearly, get 2 months free)
 *  - Feature entitlements (for plan comparison tables)
 */
public final class SubscriptionExtension implements ProductExtension {

    public static final String CONTEXT = "subscription";

    private final Money monthlyPrice;
    private final Money annualPrice;         // null = no annual plan
    private final Money setupFee;            // one-time, null = no setup fee
    private final BillingInterval defaultInterval;
    private final int trialDays;
    private final boolean seatBased;
    private final int includedSeats;
    private final Money pricePerAdditionalSeat;
    private final int maxSeats;              // 0 = unlimited
    private final List<String> entitlements; // feature flags/keys

    private SubscriptionExtension(Builder b) {
        this.monthlyPrice            = Objects.requireNonNull(b.monthlyPrice, "monthlyPrice");
        this.annualPrice             = b.annualPrice;
        this.setupFee                = b.setupFee;
        this.defaultInterval         = b.defaultInterval != null ? b.defaultInterval : BillingInterval.MONTHLY;
        this.trialDays               = Math.max(0, b.trialDays);
        this.seatBased               = b.seatBased;
        this.includedSeats           = Math.max(1, b.includedSeats);
        this.pricePerAdditionalSeat  = b.pricePerAdditionalSeat;
        this.maxSeats                = b.maxSeats;
        this.entitlements            = b.entitlements != null
                ? Collections.unmodifiableList(b.entitlements) : List.of();
    }

    @Override public String getContext() { return CONTEXT; }

    public boolean hasTrial()       { return trialDays > 0; }
    public boolean hasAnnualPlan()  { return annualPrice != null; }
    public boolean hasSetupFee()    { return setupFee != null && !setupFee.isZero(); }

    /**
     * Annual saving if customer switches from monthly to annual billing.
     */
    public Money annualSaving() {
        if (annualPrice == null) return Money.zero(monthlyPrice.getCurrencyCode());
        Money yearlyMonthly = monthlyPrice.multiply(12);
        if (yearlyMonthly.isGreaterThan(annualPrice)) {
            return yearlyMonthly.subtract(annualPrice);
        }
        return Money.zero(monthlyPrice.getCurrencyCode());
    }

    public Money getMonthlyPrice()                { return monthlyPrice; }
    public Money getAnnualPrice()                 { return annualPrice; }
    public Money getSetupFee()                    { return setupFee; }
    public BillingInterval getDefaultInterval()   { return defaultInterval; }
    public int getTrialDays()                     { return trialDays; }
    public boolean isSeatBased()                  { return seatBased; }
    public int getIncludedSeats()                 { return includedSeats; }
    public Money getPricePerAdditionalSeat()      { return pricePerAdditionalSeat; }
    public int getMaxSeats()                      { return maxSeats; }
    public List<String> getEntitlements()         { return entitlements; }

    public static Builder builder(Money monthlyPrice) { return new Builder(monthlyPrice); }

    public static final class Builder {
        private final Money monthlyPrice;
        private Money annualPrice;
        private Money setupFee;
        private BillingInterval defaultInterval;
        private int trialDays;
        private boolean seatBased;
        private int includedSeats = 1;
        private Money pricePerAdditionalSeat;
        private int maxSeats;
        private List<String> entitlements;

        private Builder(Money m) { this.monthlyPrice = m; }

        public Builder annualPrice(Money m)               { this.annualPrice = m; return this; }
        public Builder setupFee(Money f)                  { this.setupFee = f; return this; }
        public Builder defaultInterval(BillingInterval i) { this.defaultInterval = i; return this; }
        public Builder trialDays(int d)                   { this.trialDays = d; return this; }
        public Builder seatBased(boolean s)               { this.seatBased = s; return this; }
        public Builder includedSeats(int s)               { this.includedSeats = s; return this; }
        public Builder pricePerAdditionalSeat(Money p)    { this.pricePerAdditionalSeat = p; return this; }
        public Builder maxSeats(int m)                    { this.maxSeats = m; return this; }
        public Builder entitlements(List<String> e)       { this.entitlements = e; return this; }

        public SubscriptionExtension build() { return new SubscriptionExtension(this); }
    }

    public enum BillingInterval { MONTHLY, QUARTERLY, ANNUAL }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Behavior
// ─────────────────────────────────────────────────────────────────────────────

@ApplicationScoped
class SubscriptionBehavior implements ProductBehavior {

    @Override
    public String getContext() { return SubscriptionExtension.CONTEXT; }

    @Override
    public PricingResult calculatePrice(ProductAggregate product, PricingContext ctx) {
        SubscriptionExtension ext = product.requireExtension(SubscriptionExtension.CONTEXT);

        // Resolve billing interval from context hint
        String intervalHint = ctx.getHints().getOrDefault("interval", "monthly");
        SubscriptionExtension.BillingInterval interval =
                switch (intervalHint.toLowerCase()) {
                    case "annual", "yearly" -> SubscriptionExtension.BillingInterval.ANNUAL;
                    case "quarterly"        -> SubscriptionExtension.BillingInterval.QUARTERLY;
                    default                 -> SubscriptionExtension.BillingInterval.MONTHLY;
                };

        // Base price per billing period
        Money basePrice = switch (interval) {
            case ANNUAL    -> ext.hasAnnualPlan() ? ext.getAnnualPrice() : ext.getMonthlyPrice().multiply(12);
            case QUARTERLY -> ext.getMonthlyPrice().multiply(3);
            case MONTHLY   -> ext.getMonthlyPrice();
        };

        PricingResult.Builder result = PricingResult.builder(basePrice)
                .quantity(1)
                .line(interval + " plan", basePrice, "BASE");

        // Seat overage
        int seats = Integer.parseInt(ctx.getHints().getOrDefault("seats", "1"));
        if (ext.isSeatBased() && seats > ext.getIncludedSeats()) {
            int extraSeats = seats - ext.getIncludedSeats();
            if (ext.getPricePerAdditionalSeat() != null) {
                Money seatCost = ext.getPricePerAdditionalSeat().multiply(extraSeats);
                result.line(extraSeats + " additional seat(s)", seatCost, "FEE");
            }
        }

        // Setup fee (first time, signalled by hint)
        if (ext.hasSetupFee() && "true".equals(ctx.getHints().get("includeSetupFee"))) {
            result.line("Setup fee (one-time)", ext.getSetupFee(), "FEE");
        }

        return result.build();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Validator
// ─────────────────────────────────────────────────────────────────────────────

@ApplicationScoped
class SubscriptionValidator implements ProductValidator {

    @Override
    public String supports() { return SubscriptionExtension.CONTEXT; }

    @Override
    public void validate(ProductAggregate product) {
        SubscriptionExtension ext = product.requireExtension(SubscriptionExtension.CONTEXT);
        List<String> violations = new ArrayList<>();

        if (ext.getMonthlyPrice().isZero()) {
            violations.add("subscription: monthly price must be greater than zero");
        }
        if (ext.isSeatBased() && ext.getPricePerAdditionalSeat() == null) {
            violations.add("subscription: seat-based plan must define pricePerAdditionalSeat");
        }
        if (ext.isSeatBased() && ext.getMaxSeats() > 0
                && ext.getIncludedSeats() > ext.getMaxSeats()) {
            violations.add("subscription: includedSeats cannot exceed maxSeats");
        }
        if (ext.hasAnnualPlan()) {
            Money yearlyMonthly = ext.getMonthlyPrice().multiply(12);
            if (ext.getAnnualPrice().isGreaterThan(yearlyMonthly)) {
                violations.add("subscription: annual price should be less than 12x monthly price");
            }
        }

        if (!violations.isEmpty()) throw new ProductValidationException(violations);
    }
}
