package tech.kayys.numou.billing.domain.valueobject;

public enum BillingCycleStatus {
    PENDING("Pending"),
    SUCCESS("Success"),
    FAILED("Failed"),
    RETRY("Retry");

    private final String description;

    BillingCycleStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
