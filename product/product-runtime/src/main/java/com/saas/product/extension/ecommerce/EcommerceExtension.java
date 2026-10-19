package com.saas.product.extension.ecommerce;

import com.saas.product.core.model.Money;
import com.saas.product.spi.ProductExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * E-commerce domain extension.
 *
 * Carries data relevant to online retail:
 *  - Base price + compare-at price (for strike-through display)
 *  - Stock quantity and reorder threshold
 *  - Shipping weight/dimensions
 *  - Tier pricing for wholesale/bulk
 *  - Variant linking (parent product ID)
 */
public final class EcommerceExtension implements ProductExtension {

    public static final String CONTEXT = "ecommerce";

    private final Money basePrice;
    private final Money compareAtPrice;   // shown as "was" price, null = no strike-through
    private final Money costPrice;        // COGS for margin calculation
    private final int stockQuantity;
    private final int lowStockThreshold;
    private final boolean trackInventory;
    private final boolean allowBackorder;
    private final ShippingProfile shippingProfile;
    private final List<TierPrice> tierPrices;
    private final String parentVariantId; // null if not a variant
    private final List<String> variantOptionKeys; // e.g. ["size", "color"]

    private EcommerceExtension(Builder b) {
        this.basePrice          = Objects.requireNonNull(b.basePrice, "basePrice");
        this.compareAtPrice     = b.compareAtPrice;
        this.costPrice          = b.costPrice;
        this.stockQuantity      = Math.max(0, b.stockQuantity);
        this.lowStockThreshold  = Math.max(0, b.lowStockThreshold);
        this.trackInventory     = b.trackInventory;
        this.allowBackorder     = b.allowBackorder;
        this.shippingProfile    = b.shippingProfile;
        this.tierPrices         = b.tierPrices != null
                ? Collections.unmodifiableList(b.tierPrices)
                : List.of();
        this.parentVariantId    = b.parentVariantId;
        this.variantOptionKeys  = b.variantOptionKeys != null
                ? Collections.unmodifiableList(b.variantOptionKeys)
                : List.of();
    }

    @Override
    public String getContext() { return CONTEXT; }

    @Override
    public String describe() {
        return "EcommerceExtension{price=" + basePrice
                + ", stock=" + stockQuantity
                + ", trackInventory=" + trackInventory + "}";
    }

    // ---- Business queries ----

    public boolean isInStock() {
        return !trackInventory || stockQuantity > 0 || allowBackorder;
    }

    public boolean isLowStock() {
        return trackInventory && stockQuantity > 0 && stockQuantity <= lowStockThreshold;
    }

    public boolean hasDiscount() {
        return compareAtPrice != null && compareAtPrice.isGreaterThan(basePrice);
    }

    /**
     * Find the best (lowest) applicable tier price for a given quantity.
     * Falls back to basePrice if no tier matches.
     */
    public Money priceForQuantity(int quantity) {
        return tierPrices.stream()
                .filter(t -> quantity >= t.minQuantity())
                .min((a, b2) -> b2.minQuantity() - a.minQuantity())
                .map(TierPrice::unitPrice)
                .orElse(basePrice);
    }

    public BigDecimal marginPercent() {
        if (costPrice == null || costPrice.isZero()) return BigDecimal.ZERO;
        return basePrice.getAmount().subtract(costPrice.getAmount())
                .divide(basePrice.getAmount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // ---- Getters ----

    public Money getBasePrice()         { return basePrice; }
    public Money getCompareAtPrice()    { return compareAtPrice; }
    public Money getCostPrice()         { return costPrice; }
    public int getStockQuantity()       { return stockQuantity; }
    public int getLowStockThreshold()   { return lowStockThreshold; }
    public boolean isTrackInventory()   { return trackInventory; }
    public boolean isAllowBackorder()   { return allowBackorder; }
    public ShippingProfile getShippingProfile() { return shippingProfile; }
    public List<TierPrice> getTierPrices()      { return tierPrices; }
    public String getParentVariantId()           { return parentVariantId; }
    public List<String> getVariantOptionKeys()   { return variantOptionKeys; }

    // ---- Builder ----

    public static Builder builder(Money basePrice) {
        return new Builder(basePrice);
    }

    public static final class Builder {
        private final Money basePrice;
        private Money compareAtPrice;
        private Money costPrice;
        private int stockQuantity;
        private int lowStockThreshold = 5;
        private boolean trackInventory = true;
        private boolean allowBackorder = false;
        private ShippingProfile shippingProfile;
        private List<TierPrice> tierPrices;
        private String parentVariantId;
        private List<String> variantOptionKeys;

        private Builder(Money basePrice) { this.basePrice = basePrice; }

        public Builder compareAtPrice(Money m)          { this.compareAtPrice = m; return this; }
        public Builder costPrice(Money m)               { this.costPrice = m; return this; }
        public Builder stockQuantity(int q)             { this.stockQuantity = q; return this; }
        public Builder lowStockThreshold(int t)         { this.lowStockThreshold = t; return this; }
        public Builder trackInventory(boolean t)        { this.trackInventory = t; return this; }
        public Builder allowBackorder(boolean a)        { this.allowBackorder = a; return this; }
        public Builder shippingProfile(ShippingProfile s) { this.shippingProfile = s; return this; }
        public Builder tierPrices(List<TierPrice> t)   { this.tierPrices = t; return this; }
        public Builder parentVariantId(String p)        { this.parentVariantId = p; return this; }
        public Builder variantOptionKeys(List<String> k) { this.variantOptionKeys = k; return this; }

        public EcommerceExtension build() { return new EcommerceExtension(this); }
    }

    // ---- Nested types ----

    /**
     * Bulk/wholesale tier pricing: buy 10+ at a lower unit price.
     */
    public record TierPrice(int minQuantity, Money unitPrice) {}

    /**
     * Physical shipping dimensions & weight for carrier rate calculation.
     */
    public record ShippingProfile(
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            boolean requiresRefrigeration,
            boolean isDangerous
    ) {}
}
