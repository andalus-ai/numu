package tech.kayys.numou.billing.domain.valueobject;

/**
 * Status of a billing schedule.
 */
public enum BillingStatus {
    ACTIVE("Active - Billing in progress"),
    PAUSED("Paused - Billing temporarily stopped"),
    CANCELLED("Cancelled - Billing terminated"),
    COMPLETED("Completed - All billing cycles done"),
    FAILED("Failed - Payment failures exceeded limit"),
    PENDING_ACTIVATION("Pending Activation - Not yet started");

    private final String description;

    BillingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == ACTIVE || this == PENDING_ACTIVATION;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED || this == FAILED;
    }
}