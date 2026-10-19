package tech.kayys.numou.billing.domain.valueobject;

/**
 * Identifies the business domain origin for billing operations.
 * Allows routing to domain-specific plugins (SaaS, FnB, Retail, Grocery, etc.)
 */
public enum DomainTag {
    GENERIC("Generic Billing"),
    SAAS("Software as a Service"),
    FNB("Food and Beverage"),
    RETAIL("Retail and Point-of-Sale"),
    GROCERY("Grocery and Supermarket"),
    ECOMMERCE("E-Commerce"),
    PROFESSIONAL_SERVICES("Professional & Consulting Services"),
    CUSTOM("Custom Industry Extension");

    private final String displayName;

    DomainTag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
