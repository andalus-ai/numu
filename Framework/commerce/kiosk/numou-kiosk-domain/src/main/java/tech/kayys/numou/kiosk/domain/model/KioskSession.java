package tech.kayys.syirkah.kiosk.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.syirkah.kiosk.domain.valueobject.SessionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kiosk Session aggregate root.
 * Represents a customer session on a self-service kiosk.
 */
public final class KioskSession extends AbstractAggregateRoot<KioskSessionId> {
    
    private static final long serialVersionUID = 1L;
    
    private UUID kioskId;
    private UUID cartId;
    private String customerId; // Null for guest checkout
    private SessionStatus status;
    private String language;
    private String currencyCode;
    private Instant startedAt;
    private Instant endedAt;
    private Instant lastActivityAt;
    private List<KioskInteraction> interactions;
    private boolean isAssisted;
    private String assistanceRequired;
    private boolean ageVerificationRequired;
    private boolean ageVerified;
    private String receiptEmail;
    private String receiptPhone;
    private boolean digitalReceiptRequested;
    private boolean paperReceiptRequested;
    private String paymentMethod;
    private String transactionId;
    private int idleTimeSeconds;

    private KioskSession(KioskSessionId id) {
        super(id);
        this.status = SessionStatus.STARTED;
        this.startedAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.interactions = new ArrayList<>();
        this.isAssisted = false;
        this.ageVerificationRequired = false;
        this.ageVerified = false;
        this.paperReceiptRequested = true;
        this.idleTimeSeconds = 0;
    }

    private KioskSession() {
        super();
    }

    /**
     * Factory method to create a new kiosk session.
     */
    public static KioskSession create(
            KioskSessionId id,
            UUID kioskId,
            String language,
            String currencyCode) {
        KioskSession session = new KioskSession(id);
        session.kioskId = kioskId;
        session.language = language != null ? language : "en";
        session.currencyCode = currencyCode != null ? currencyCode : "USD";
        return session;
    }

    /**
     * Sets the cart ID for the session.
     */
    public void setCartId(UUID cartId) {
        this.cartId = cartId;
        this.status = SessionStatus.IN_PROGRESS;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the customer ID for the session.
     */
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an interaction to the session.
     */
    public void addInteraction(KioskInteraction interaction) {
        interactions.add(interaction);
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the last activity time.
     */
    public void updateLastActivity() {
        this.lastActivityAt = Instant.now();
        this.idleTimeSeconds = 0;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Increments idle time.
     */
    public void incrementIdleTime(int seconds) {
        this.idleTimeSeconds += seconds;
        if (this.idleTimeSeconds > 300) { // 5 minutes
            this.status = SessionStatus.TIMED_OUT;
            this.endedAt = Instant.now();
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Requests assistance.
     */
    public void requestAssistance(String reason) {
        this.isAssisted = true;
        this.assistanceRequired = reason;
        this.status = SessionStatus.IN_PROGRESS;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Verifies age for restricted items.
     */
    public void verifyAge(boolean verified) {
        this.ageVerified = verified;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Completes the session.
     */
    public void complete() {
        if (status.isTerminal()) {
            throw new IllegalStateException("Session is already in terminal state");
        }
        this.status = SessionStatus.COMPLETED;
        this.endedAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Ends the session.
     */
    public void end() {
        complete();
    }

    /**
     * Cancels the session.
     */
    public void cancel(String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Session is already in terminal state");
        }
        this.status = SessionStatus.CANCELLED;
        this.endedAt = Instant.now();
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Abandons the session.
     */
    public void abandon() {
        this.status = SessionStatus.ABANDONED;
        this.endedAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Starts checkout process.
     */
    public void startCheckout() {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot checkout in status: " + status);
        }
        if (cartId == null) {
            throw new IllegalStateException("No cart associated with session");
        }
        this.status = SessionStatus.CHECKING_OUT;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets session duration in seconds.
     */
    public long getDurationSeconds() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).getSeconds();
    }

    /**
     * Gets the number of items scanned.
     */
    public long getItemsScanned() {
        return interactions.stream()
            .filter(i -> i.getType() == KioskInteractionType.ITEM_SCANNED)
            .count();
    }

    /**
     * Checks if session is active.
     */
    public boolean isActive() {
        return status.isActive() && !status.isTerminal();
    }

    // Getters and Setters
    public UUID getKioskId() { return kioskId; }
    public UUID getCartId() { return cartId; }
    public String getCustomerId() { return customerId; }
    public SessionStatus getStatus() { return status; }
    public String getLanguage() { return language; }
    public String getCurrencyCode() { return currencyCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public List<KioskInteraction> getInteractions() { return Collections.unmodifiableList(interactions); }
    public boolean isAssisted() { return isAssisted; }
    public String getAssistanceRequired() { return assistanceRequired; }
    public boolean isAgeVerificationRequired() { return ageVerificationRequired; }
    public boolean isAgeVerified() { return ageVerified; }
    public String getReceiptEmail() { return receiptEmail; }
    public String getReceiptPhone() { return receiptPhone; }
    public boolean isDigitalReceiptRequested() { return digitalReceiptRequested; }
    public boolean isPaperReceiptRequested() { return paperReceiptRequested; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionId() { return transactionId; }
    public int getIdleTimeSeconds() { return idleTimeSeconds; }

    public void setAgeVerificationRequired(boolean ageVerificationRequired) {
        this.ageVerificationRequired = ageVerificationRequired;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setReceiptEmail(String receiptEmail) {
        this.receiptEmail = receiptEmail;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setReceiptPhone(String receiptPhone) {
        this.receiptPhone = receiptPhone;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setDigitalReceiptRequested(boolean digitalReceiptRequested) {
        this.digitalReceiptRequested = digitalReceiptRequested;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPaperReceiptRequested(boolean paperReceiptRequested) {
        this.paperReceiptRequested = paperReceiptRequested;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
        updateLastActivity();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "KioskSession{" +
                "id=" + getId() +
                ", kioskId=" + kioskId +
                ", status=" + status +
                ", itemsScanned=" + getItemsScanned() +
                '}';
    }

    /**
     * Kiosk interaction type enum.
     */
    public enum KioskInteractionType {
        SESSION_START("Session Started"),
        ITEM_SCANNED("Item Scanned"),
        ITEM_WEIGHED("Item Weighed"),
        ITEM_REMOVED("Item Removed"),
        ITEM_ADDED_MANUALLY("Item Added Manually"),
        PRICE_CHECK("Price Check"),
        PAYMENT_STARTED("Payment Started"),
        PAYMENT_COMPLETED("Payment Completed"),
        PAYMENT_FAILED("Payment Failed"),
        RECEIPT_PRINTED("Receipt Printed"),
        ASSISTANCE_REQUESTED("Assistance Requested"),
        ASSISTANCE_RECEIVED("Assistance Received"),
        AGE_VERIFICATION("Age Verification"),
        COUPON_APPLIED("Coupon Applied"),
        COUPON_REMOVED("Coupon Removed");

        private final String description;

        KioskInteractionType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Kiosk interaction value object.
     */
    public static final class KioskInteraction implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String interactionId;
        private final KioskInteractionType type;
        private final String description;
        private final Instant timestamp;
        private final Map<String, String> metadata;

        public KioskInteraction(
                String interactionId,
                KioskInteractionType type,
                String description,
                Map<String, String> metadata) {
            this.interactionId = interactionId;
            this.type = type;
            this.description = description;
            this.timestamp = Instant.now();
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            validate();
        }

        @Override
        public void validate() {
            if (type == null) {
                throw new IllegalArgumentException("Interaction type cannot be null");
            }
        }

        public String getInteractionId() { return interactionId; }
        public KioskInteractionType getType() { return type; }
        public String getDescription() { return description; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

        @Override
        public String toString() {
            return "KioskInteraction{" +
                    "type=" + type +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}