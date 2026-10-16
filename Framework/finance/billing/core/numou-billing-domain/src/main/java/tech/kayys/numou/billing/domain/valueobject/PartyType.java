package tech.kayys.numou.billing.domain.valueobject;

/**
 * Identifies the type of party being billed.
 * Enables neutral billing across tenants, retail customers, B2B merchants, etc.
 */
public enum PartyType {
    CUSTOMER("Individual Customer"),
    TENANT("SaaS Multi-tenant Account"),
    MERCHANT("B2B Merchant / Vendor"),
    ORGANIZATION("Corporate Organization"),
    PARTNER("Channel / Reseller Partner"),
    GUEST("Walk-in / FnB Guest"),
    INTERNAL("Internal Cost Center");

    private final String description;

    PartyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
