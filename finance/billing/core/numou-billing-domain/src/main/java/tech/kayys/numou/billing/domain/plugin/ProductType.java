package tech.kayys.numou.billing.domain.plugin;

public enum ProductType {
    SAAS_SUBSCRIPTION("SaaS Subscription"),
    SAAS_USAGE_BASED("SaaS Usage-Based"),
    SAAS_TIERED("SaaS Tiered Pricing"),
    RETAIL_POS("Retail Point-of-Sale"),
    RETAIL_LAYAWAY("Retail Layaway Installment"),
    FNB_DINE_IN("FnB Dine-In Order"),
    FNB_TAKEAWAY("FnB Takeaway"),
    GROCERY_WEIGHT_BASED("Grocery Scale/Weight-Based"),
    GROCERY_BATCH("Grocery Batch / Packaged Item"),
    SERVICE("Professional Service"),
    CUSTOM("Custom Product");

    private final String displayName;

    ProductType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
