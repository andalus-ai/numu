package tech.kayys.numou.billing.domain.valueobject;

/**
 * Dunning actions for payment reminders.
 */
public enum DunningAction {
    EMAIL_REMINDER("Email Reminder"),
    SMS_REMINDER("SMS Reminder"),
    INVOICE_UPDATE("Invoice Update"),
    PAYMENT_RETRY("Payment Retry"),
    SUSPEND_SERVICE("Suspend Service"),
    TERMINATE_SERVICE("Terminate Service"),
    COLLECTIONS_REFERRAL("Collections Referral");

    private final String description;

    DunningAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCommunication() {
        return this == EMAIL_REMINDER || this == SMS_REMINDER;
    }

    public boolean isEscalation() {
        return this == SUSPEND_SERVICE || this == TERMINATE_SERVICE || this == COLLECTIONS_REFERRAL;
    }
}