package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.billing.domain.valueobject.DunningLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Billing Schedule aggregate root.
 * Represents a recurring billing schedule for a subscription.
 */
public final class BillingSchedule extends AbstractAggregateRoot<BillingScheduleId> {
    
    private static final long serialVersionUID = 1L;
    
    private UUID subscriptionId;
    private String customerId;
    private String customerEmail;
    private BillingFrequency frequency;
    private BillingStatus status;
    private Instant startDate;
    private Instant endDate;
    private Instant nextBillingDate;
    private Instant lastBillingDate;
    private Money amount;
    private String currencyCode;
    private String paymentMethodToken;
    private List<BillingCycle> billingCycles;
    private List<DunningEvent> dunningEvents;
    private int currentCycle;
    private int totalCycles;
    private int failedPaymentCount;
    private int maxFailedPayments;
    private boolean sendEmailNotifications;
    private boolean sendSmsNotifications;
    private String createdBy;
    private String updatedBy;
    private boolean active;

    private BillingSchedule(BillingScheduleId id) {
        super(id);
        this.billingCycles = new ArrayList<>();
        this.dunningEvents = new ArrayList<>();
        this.status = BillingStatus.PENDING_ACTIVATION;
        this.currentCycle = 0;
        this.totalCycles = 0;
        this.failedPaymentCount = 0;
        this.maxFailedPayments = 3;
        this.sendEmailNotifications = true;
        this.sendSmsNotifications = false;
        this.active = true;
    }

    private BillingSchedule() {
        super();
    }

    /**
     * Factory method to create a new billing schedule.
     */
    public static BillingSchedule create(
            BillingScheduleId id,
            UUID subscriptionId,
            String customerId,
            BillingFrequency frequency,
            Money amount,
            String currencyCode,
            Instant startDate) {
        BillingSchedule schedule = new BillingSchedule(id);
        schedule.subscriptionId = subscriptionId;
        schedule.customerId = customerId;
        schedule.frequency = frequency;
        schedule.amount = amount;
        schedule.currencyCode = currencyCode;
        schedule.startDate = startDate;
        schedule.nextBillingDate = calculateNextBillingDate(startDate, frequency);
        return schedule;
    }

    /**
     * Activates the billing schedule.
     */
    public void activate() {
        if (status != BillingStatus.PENDING_ACTIVATION && status != BillingStatus.PAUSED) {
            throw new IllegalStateException("Cannot activate schedule in status: " + status);
        }
        this.status = BillingStatus.ACTIVE;
        this.nextBillingDate = calculateNextBillingDate(Instant.now(), frequency);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Pauses the billing schedule.
     */
    public void pause() {
        if (status != BillingStatus.ACTIVE) {
            throw new IllegalStateException("Cannot pause schedule in status: " + status);
        }
        this.status = BillingStatus.PAUSED;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Cancels the billing schedule.
     */
    public void cancel(String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Schedule is already terminated");
        }
        this.status = BillingStatus.CANCELLED;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Processes a billing cycle.
     */
    public BillingCycle processBillingCycle(Instant billingDate, String invoiceId) {
        if (status != BillingStatus.ACTIVE) {
            throw new IllegalStateException("Cannot process billing in status: " + status);
        }
        if (billingDate.isBefore(nextBillingDate)) {
            throw new IllegalArgumentException("Billing date is before next billing date");
        }

        BillingCycle cycle = new BillingCycle(
            currentCycle + 1,
            billingDate,
            amount,
            currencyCode,
            invoiceId,
            BillingCycleStatus.PENDING
        );
        billingCycles.add(cycle);
        currentCycle++;
        
        // Update next billing date
        this.nextBillingDate = calculateNextBillingDate(billingDate, frequency);
        this.lastBillingDate = billingDate;
        
        // Check if completed
        if (totalCycles > 0 && currentCycle >= totalCycles) {
            this.status = BillingStatus.COMPLETED;
        }
        
        setUpdatedAt(Instant.now());
        incrementVersion();
        return cycle;
    }

    /**
     * Marks a billing cycle as successful.
     */
    public void markCycleSuccess(int cycleNumber, String transactionId) {
        BillingCycle cycle = findCycle(cycleNumber);
        cycle.markSuccess(transactionId);
        this.failedPaymentCount = 0;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks a billing cycle as failed.
     */
    public void markCycleFailed(int cycleNumber, String errorMessage) {
        BillingCycle cycle = findCycle(cycleNumber);
        cycle.markFailed(errorMessage);
        this.failedPaymentCount++;
        
        // Add dunning event
        addDunningEvent(DunningAction.PAYMENT_RETRY, "Payment failed: " + errorMessage);
        
        // Check if max failures exceeded
        if (failedPaymentCount >= maxFailedPayments) {
            this.status = BillingStatus.FAILED;
            this.active = false;
        }
        
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a dunning event.
     */
    public void addDunningEvent(DunningAction action, String notes) {
        DunningEvent event = new DunningEvent(
            UUID.randomUUID().toString(),
            action,
            notes,
            Instant.now(),
            DunningEventStatus.SENT
        );
        dunningEvents.add(event);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the next billing date.
     */
    public Instant getNextBillingDate() {
        return nextBillingDate;
    }

    /**
     * Calculates the next billing date.
     */
    private static Instant calculateNextBillingDate(Instant from, BillingFrequency frequency) {
        LocalDate date = from.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate next = switch (frequency) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case BI_WEEKLY -> date.plusWeeks(2);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case SEMI_ANNUAL -> date.plusMonths(6);
            case ANNUAL -> date.plusYears(1);
            case BIENNIAL -> date.plusYears(2);
            case CUSTOM -> date.plusMonths(1); // Default to monthly
        };
        return next.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private BillingCycle findCycle(int cycleNumber) {
        return billingCycles.stream()
            .filter(c -> c.cycleNumber == cycleNumber)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Cycle not found: " + cycleNumber));
    }

    // Getters
    public UUID getSubscriptionId() { return subscriptionId; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public BillingFrequency getFrequency() { return frequency; }
    public BillingStatus getStatus() { return status; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public Instant getLastBillingDate() { return lastBillingDate; }
    public Money getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getPaymentMethodToken() { return paymentMethodToken; }
    public List<BillingCycle> getBillingCycles() { return Collections.unmodifiableList(billingCycles); }
    public List<DunningEvent> getDunningEvents() { return Collections.unmodifiableList(dunningEvents); }
    public int getCurrentCycle() { return currentCycle; }
    public int getTotalCycles() { return totalCycles; }
    public int getFailedPaymentCount() { return failedPaymentCount; }
    public int getMaxFailedPayments() { return maxFailedPayments; }
    public boolean isSendEmailNotifications() { return sendEmailNotifications; }
    public boolean isSendSmsNotifications() { return sendSmsNotifications; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public boolean isActive() { return active; }

    // Setters
    public void setStatus(BillingStatus status) {
        this.status = status;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setActive(boolean active) {
        this.active = active;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setEndDate(Instant endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        this.endDate = endDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPaymentMethodToken(String paymentMethodToken) {
        this.paymentMethodToken = paymentMethodToken;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTotalCycles(int totalCycles) {
        if (totalCycles < 0) {
            throw new IllegalArgumentException("Total cycles cannot be negative");
        }
        this.totalCycles = totalCycles;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setMaxFailedPayments(int maxFailedPayments) {
        if (maxFailedPayments < 1) {
            throw new IllegalArgumentException("Max failed payments must be at least 1");
        }
        this.maxFailedPayments = maxFailedPayments;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSendEmailNotifications(boolean sendEmailNotifications) {
        this.sendEmailNotifications = sendEmailNotifications;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSendSmsNotifications(boolean sendSmsNotifications) {
        this.sendSmsNotifications = sendSmsNotifications;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the next billing date string for display.
     */
    public String getNextBillingDateFormatted() {
        if (nextBillingDate == null) {
            return "N/A";
        }
        return nextBillingDate.atZone(ZoneId.systemDefault())
            .toLocalDate().toString();
    }

    /**
     * Gets the billing status description.
     */
    public String getStatusDescription() {
        return status.getDescription();
    }

    @Override
    public String toString() {
        return "BillingSchedule{" +
                "id=" + getId() +
                ", subscriptionId=" + subscriptionId +
                ", customerId='" + customerId + '\'' +
                ", frequency=" + frequency +
                ", status=" + status +
                ", amount=" + amount +
                '}';
    }

    /**
     * Billing cycle record.
     */
    public static final class BillingCycle {
        private final int cycleNumber;
        private final Instant billingDate;
        private final Money amount;
        private final String currencyCode;
        private final String invoiceId;
        private BillingCycleStatus status;
        private String transactionId;
        private String errorMessage;
        private Instant processedAt;
        private Instant completedAt;

        public BillingCycle(
                int cycleNumber,
                Instant billingDate,
                Money amount,
                String currencyCode,
                String invoiceId,
                BillingCycleStatus status) {
            this.cycleNumber = cycleNumber;
            this.billingDate = billingDate;
            this.amount = amount;
            this.currencyCode = currencyCode;
            this.invoiceId = invoiceId;
            this.status = status;
            this.processedAt = Instant.now();
        }

        public int getCycleNumber() { return cycleNumber; }
        public Instant getBillingDate() { return billingDate; }
        public Money getAmount() { return amount; }
        public String getCurrencyCode() { return currencyCode; }
        public String getInvoiceId() { return invoiceId; }
        public BillingCycleStatus getStatus() { return status; }
        public String getTransactionId() { return transactionId; }
        public String getErrorMessage() { return errorMessage; }
        public Instant getProcessedAt() { return processedAt; }
        public Instant getCompletedAt() { return completedAt; }

        public void markSuccess(String transactionId) {
            this.status = BillingCycleStatus.SUCCESS;
            this.transactionId = transactionId;
            this.completedAt = Instant.now();
        }

        public void markFailed(String errorMessage) {
            this.status = BillingCycleStatus.FAILED;
            this.errorMessage = errorMessage;
            this.completedAt = Instant.now();
        }

        @Override
        public String toString() {
            return "BillingCycle{" +
                    "cycleNumber=" + cycleNumber +
                    ", billingDate=" + billingDate +
                    ", status=" + status +
                    ", amount=" + amount +
                    '}';
        }
    }

    /**
     * Billing cycle status enum.
     */
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

    /**
     * Dunning event record.
     */
    public static final class DunningEvent {
        private final String eventId;
        private final DunningAction action;
        private final String notes;
        private final Instant timestamp;
        private final DunningEventStatus status;

        public DunningEvent(
                String eventId,
                DunningAction action,
                String notes,
                Instant timestamp,
                DunningEventStatus status) {
            this.eventId = eventId;
            this.action = action;
            this.notes = notes;
            this.timestamp = timestamp;
            this.status = status;
        }

        public String getEventId() { return eventId; }
        public DunningAction getAction() { return action; }
        public String getNotes() { return notes; }
        public Instant getTimestamp() { return timestamp; }
        public DunningEventStatus getStatus() { return status; }

        @Override
        public String toString() {
            return "DunningEvent{" +
                    "action=" + action +
                    ", timestamp=" + timestamp +
                    ", status=" + status +
                    '}';
        }
    }

    /**
     * Dunning event status enum.
     */
    public enum DunningEventStatus {
        SENT("Sent"),
        DELIVERED("Delivered"),
        READ("Read"),
        ACTIONED("Actioned"),
        IGNORED("Ignored");

        private final String description;

        DunningEventStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}