package tech.kayys.payment.model;

public enum PaymentStatus {
    PENDING("Pending"),
    PROCESSING("Processing"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    REFUNDED("Refunded"),
    PARTIAL_REFUNDED("Partial Refunded"),
    CHARGEBACK("Chargeback"),
    DISPUTE("Dispute"),
    SETTLEMENT("Settlement"),
    COMPLETED("Completed");

    private final String displayName;

    PaymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
