package tech.kayys.numou.billing.domain.valueobject;

/**
 * Billing frequency for recurring billing.
 */
public enum BillingFrequency {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    BI_WEEKLY("Bi-Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    SEMI_ANNUAL("Semi-Annual"),
    ANNUAL("Annual"),
    BIENNIAL("Biennial"),
    CUSTOM("Custom");

    private final String displayName;

    BillingFrequency(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDays() {
        return switch (this) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case BI_WEEKLY -> 14;
            case MONTHLY -> 30;
            case QUARTERLY -> 90;
            case SEMI_ANNUAL -> 180;
            case ANNUAL -> 365;
            case BIENNIAL -> 730;
            case CUSTOM -> 0;
        };
    }

    public boolean isFixed() {
        return this != CUSTOM;
    }
}