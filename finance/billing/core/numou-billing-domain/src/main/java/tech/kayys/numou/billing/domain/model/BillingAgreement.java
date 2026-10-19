package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.billing.domain.identifier.BillingAgreementId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Billing Agreement aggregate root.
 * Represents a customer's agreement to be billed for services.
 */
public final class BillingAgreement extends AbstractAggregateRoot<BillingAgreementId> {
    
    private static final long serialVersionUID = 1L;
    
    private String customerId;
    private String customerEmail;
    private AgreementStatus status;
    private PaymentMethod defaultPaymentMethod;
    private List<PaymentMethod> paymentMethods;
    private String billingAddress;
    private String shippingAddress;
    private String taxId;
    private String taxExemptionCertificate;
    private String paymentTerms;
    private int gracePeriodDays;
    private boolean autoPayEnabled;
    private boolean paperlessBilling;
    private String preferredLanguage;
    private String billingContactName;
    private String billingContactEmail;
    private String billingContactPhone;
    private List<BillingAgreementHistory> history;
    private String notes;
    private String createdBy;
    private boolean active;

    private BillingAgreement(BillingAgreementId id) {
        super(id);
        this.paymentMethods = new ArrayList<>();
        this.history = new ArrayList<>();
        this.status = AgreementStatus.PENDING;
        this.active = true;
        this.autoPayEnabled = true;
        this.gracePeriodDays = 5;
    }

    private BillingAgreement() {
        super();
    }

    /**
     * Factory method to create a new billing agreement.
     */
    public static BillingAgreement create(
            BillingAgreementId id,
            String customerId,
            String customerEmail,
            PaymentMethod defaultPaymentMethod) {
        BillingAgreement agreement = new BillingAgreement(id);
        agreement.customerId = customerId;
        agreement.customerEmail = customerEmail;
        agreement.defaultPaymentMethod = defaultPaymentMethod;
        agreement.paymentMethods.add(defaultPaymentMethod);
        return agreement;
    }

    /**
     * Adds a payment method to the agreement.
     */
    public void addPaymentMethod(PaymentMethod paymentMethod) {
        if (!paymentMethods.contains(paymentMethod)) {
            paymentMethods.add(paymentMethod);
            addHistory("Payment Method Added", "Added " + paymentMethod.getType().name());
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a payment method from the agreement.
     */
    public void removePaymentMethod(String paymentMethodId) {
        paymentMethods.removeIf(pm -> pm.getId().equals(paymentMethodId));
        if (paymentMethods.isEmpty()) {
            throw new IllegalStateException("At least one payment method is required");
        }
        addHistory("Payment Method Removed", "Removed payment method " + paymentMethodId);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the default payment method.
     */
    public void setDefaultPaymentMethod(String paymentMethodId) {
        PaymentMethod newDefault = paymentMethods.stream()
            .filter(pm -> pm.getId().equals(paymentMethodId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

        this.defaultPaymentMethod = newDefault;
        addHistory("Default Payment Method Changed", "Default set to " + newDefault.getType().name());
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the billing agreement.
     */
    public void activate() {
        if (status == AgreementStatus.ACTIVE) {
            return;
        }
        if (paymentMethods.isEmpty()) {
            throw new IllegalStateException("No payment methods configured");
        }
        this.status = AgreementStatus.ACTIVE;
        addHistory("Agreement Activated", "Billing agreement activated");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Suspends the billing agreement.
     */
    public void suspend(String reason) {
        if (status == AgreementStatus.SUSPENDED) {
            return;
        }
        this.status = AgreementStatus.SUSPENDED;
        addHistory("Agreement Suspended", "Reason: " + reason);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Cancels the billing agreement.
     */
    public void cancel(String reason) {
        if (status == AgreementStatus.CANCELLED) {
            return;
        }
        this.status = AgreementStatus.CANCELLED;
        this.active = false;
        addHistory("Agreement Cancelled", "Reason: " + reason);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Reactivates a cancelled agreement.
     */
    public void reactivate() {
        if (status != AgreementStatus.CANCELLED) {
            throw new IllegalStateException("Only cancelled agreements can be reactivated");
        }
        this.status = AgreementStatus.ACTIVE;
        this.active = true;
        addHistory("Agreement Reactivated", "Agreement reactivated");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates billing information.
     */
    public void updateBillingInfo(
            String billingAddress,
            String shippingAddress,
            String taxId,
            String paymentTerms) {
        this.billingAddress = billingAddress;
        this.shippingAddress = shippingAddress;
        this.taxId = taxId;
        this.paymentTerms = paymentTerms;
        addHistory("Billing Info Updated", "Billing information updated");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void addHistory(String action, String details) {
        BillingAgreementHistory historyEntry = new BillingAgreementHistory(
            UUID.randomUUID().toString(),
            action,
            details,
            Instant.now()
        );
        history.add(historyEntry);
    }

    // Getters
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public AgreementStatus getStatus() { return status; }
    public PaymentMethod getDefaultPaymentMethod() { return defaultPaymentMethod; }
    public List<PaymentMethod> getPaymentMethods() { return Collections.unmodifiableList(paymentMethods); }
    public String getBillingAddress() { return billingAddress; }
    public String getShippingAddress() { return shippingAddress; }
    public String getTaxId() { return taxId; }
    public String getTaxExemptionCertificate() { return taxExemptionCertificate; }
    public String getPaymentTerms() { return paymentTerms; }
    public int getGracePeriodDays() { return gracePeriodDays; }
    public boolean isAutoPayEnabled() { return autoPayEnabled; }
    public boolean isPaperlessBilling() { return paperlessBilling; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public String getBillingContactName() { return billingContactName; }
    public String getBillingContactEmail() { return billingContactEmail; }
    public String getBillingContactPhone() { return billingContactPhone; }
    public List<BillingAgreementHistory> getHistory() { return Collections.unmodifiableList(history); }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTaxExemptionCertificate(String taxExemptionCertificate) {
        this.taxExemptionCertificate = taxExemptionCertificate;
        addHistory("Tax Exemption Added", "Tax exemption certificate added");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setGracePeriodDays(int gracePeriodDays) {
        if (gracePeriodDays < 0) {
            throw new IllegalArgumentException("Grace period cannot be negative");
        }
        this.gracePeriodDays = gracePeriodDays;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setAutoPayEnabled(boolean autoPayEnabled) {
        this.autoPayEnabled = autoPayEnabled;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPaperlessBilling(boolean paperlessBilling) {
        this.paperlessBilling = paperlessBilling;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setBillingContactName(String billingContactName) {
        this.billingContactName = billingContactName;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setBillingContactEmail(String billingContactEmail) {
        this.billingContactEmail = billingContactEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setBillingContactPhone(String billingContactPhone) {
        this.billingContactPhone = billingContactPhone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "BillingAgreement{" +
                "id=" + getId() +
                ", customerId='" + customerId + '\'' +
                ", status=" + status +
                ", autoPayEnabled=" + autoPayEnabled +
                '}';
    }

    /**
     * Agreement status enum.
     */
    public enum AgreementStatus {
        PENDING("Pending"),
        ACTIVE("Active"),
        SUSPENDED("Suspended"),
        CANCELLED("Cancelled"),
        EXPIRED("Expired");

        private final String description;

        AgreementStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Payment method value object.
     */
    public static final class PaymentMethod implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String id;
        private final PaymentMethodType type;
        private final String lastFourDigits;
        private final String cardType;
        private final String expiryMonth;
        private final String expiryYear;
        private final String token;
        private final boolean isDefault;

        public PaymentMethod(
                String id,
                PaymentMethodType type,
                String lastFourDigits,
                String cardType,
                String expiryMonth,
                String expiryYear,
                String token,
                boolean isDefault) {
            this.id = id;
            this.type = type;
            this.lastFourDigits = lastFourDigits;
            this.cardType = cardType;
            this.expiryMonth = expiryMonth;
            this.expiryYear = expiryYear;
            this.token = token;
            this.isDefault = isDefault;
            validate();
        }

        @Override
        public void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Payment method ID cannot be empty");
            }
            if (type == null) {
                throw new IllegalArgumentException("Payment method type cannot be null");
            }
        }

        public String getId() { return id; }
        public PaymentMethodType getType() { return type; }
        public String getLastFourDigits() { return lastFourDigits; }
        public String getCardType() { return cardType; }
        public String getExpiryMonth() { return expiryMonth; }
        public String getExpiryYear() { return expiryYear; }
        public String getToken() { return token; }
        public boolean isDefault() { return isDefault; }

        public String getMaskedDisplay() {
            if (type == PaymentMethodType.CREDIT_CARD || type == PaymentMethodType.DEBIT_CARD) {
                return "•••• •••• •••• " + lastFourDigits;
            }
            return type.getDisplayName();
        }

        @Override
        public String toString() {
            return "PaymentMethod{" +
                    "type=" + type +
                    ", masked='" + getMaskedDisplay() + '\'' +
                    '}';
        }
    }

    /**
     * Payment method type enum.
     */
    public enum PaymentMethodType {
        CREDIT_CARD("Credit Card"),
        DEBIT_CARD("Debit Card"),
        BANK_ACCOUNT("Bank Account"),
        PAYPAL("PayPal"),
        APPLE_PAY("Apple Pay"),
        GOOGLE_PAY("Google Pay"),
        CASH("Cash"),
        CHECK("Check"),
        WIRE_TRANSFER("Wire Transfer");

        private final String displayName;

        PaymentMethodType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Billing agreement history record.
     */
    public static final class BillingAgreementHistory {
        private final String historyId;
        private final String action;
        private final String details;
        private final Instant timestamp;

        public BillingAgreementHistory(String historyId, String action, String details, Instant timestamp) {
            this.historyId = historyId;
            this.action = action;
            this.details = details;
            this.timestamp = timestamp;
        }

        public String getHistoryId() { return historyId; }
        public String getAction() { return action; }
        public String getDetails() { return details; }
        public Instant getTimestamp() { return timestamp; }
    }
}