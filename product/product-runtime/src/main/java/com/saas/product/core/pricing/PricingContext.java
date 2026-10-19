package com.saas.product.core.pricing;

import com.saas.product.core.model.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Input context for a pricing calculation request.
 *
 * Contains everything a behavior needs to compute a price:
 * quantity, customer segment, channel, active promotions, etc.
 */
public final class PricingContext {

    private final String tenantId;
    private final String customerId;       // null = anonymous
    private final String customerSegment;  // e.g. "vip", "wholesale", "retail"
    private final String channelId;        // e.g. "web", "pos", "delivery-app"
    private final int quantity;
    private final List<String> appliedCouponCodes;
    private final Map<String, String> hints; // extension-specific hints
    private final Instant pricingAt;

    private PricingContext(Builder b) {
        this.tenantId          = Objects.requireNonNull(b.tenantId, "tenantId");
        this.customerId        = b.customerId;
        this.customerSegment   = b.customerSegment != null ? b.customerSegment : "retail";
        this.channelId         = b.channelId != null ? b.channelId : "default";
        this.quantity          = b.quantity > 0 ? b.quantity : 1;
        this.appliedCouponCodes = b.appliedCouponCodes != null
                ? Collections.unmodifiableList(b.appliedCouponCodes)
                : List.of();
        this.hints             = b.hints != null
                ? Collections.unmodifiableMap(b.hints)
                : Map.of();
        this.pricingAt         = b.pricingAt != null ? b.pricingAt : Instant.now();
    }

    public String getTenantId()               { return tenantId; }
    public String getCustomerId()             { return customerId; }
    public String getCustomerSegment()        { return customerSegment; }
    public String getChannelId()              { return channelId; }
    public int getQuantity()                  { return quantity; }
    public List<String> getAppliedCouponCodes() { return appliedCouponCodes; }
    public Map<String, String> getHints()     { return hints; }
    public Instant getPricingAt()             { return pricingAt; }

    public boolean isAnonymous() { return customerId == null; }

    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    public static final class Builder {
        private final String tenantId;
        private String customerId;
        private String customerSegment;
        private String channelId;
        private int quantity = 1;
        private List<String> appliedCouponCodes;
        private Map<String, String> hints;
        private Instant pricingAt;

        private Builder(String tenantId) { this.tenantId = tenantId; }

        public Builder customerId(String id)                    { this.customerId = id; return this; }
        public Builder customerSegment(String s)                { this.customerSegment = s; return this; }
        public Builder channelId(String c)                      { this.channelId = c; return this; }
        public Builder quantity(int q)                          { this.quantity = q; return this; }
        public Builder coupons(List<String> c)                  { this.appliedCouponCodes = c; return this; }
        public Builder hints(Map<String, String> h)             { this.hints = h; return this; }
        public Builder pricingAt(Instant t)                     { this.pricingAt = t; return this; }

        public PricingContext build() { return new PricingContext(this); }
    }
}
