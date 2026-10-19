package tech.kayys.numou.billing.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.billing.domain.identifier.UsageRecordId;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Usage Record aggregate root.
 * Tracks metered usage for usage-based billing.
 */
public final class UsageRecord extends AbstractAggregateRoot<UsageRecordId> {
    
    private static final long serialVersionUID = 1L;
    
    private String customerId;
    private String subscriptionId;
    private String meterId; // e.g., API_CALLS, STORAGE_GB, USER_SEATS
    private Instant usageDate;
    private double quantity;
    private String unit; // e.g., CALLS, GB, SEATS
    private Map<String, String> metadata;
    private String source; // e.g., API, SYSTEM, IMPORT
    private boolean invoiced;
    private String invoiceId;
    private String aggregatedPeriod; // e.g., 2024-01
    private String createdBy;

    private UsageRecord(UsageRecordId id) {
        super(id);
        this.metadata = new HashMap<>();
        this.invoiced = false;
        this.usageDate = Instant.now();
    }

    private UsageRecord() {
        super();
    }

    /**
     * Factory method to create a new usage record.
     */
    public static UsageRecord create(
            UsageRecordId id,
            String customerId,
            String subscriptionId,
            String meterId,
            double quantity,
            String unit) {
        UsageRecord record = new UsageRecord(id);
        record.customerId = customerId;
        record.subscriptionId = subscriptionId;
        record.meterId = meterId;
        record.quantity = quantity;
        record.unit = unit;
        return record;
    }

    /**
     * Adds metadata to the usage record.
     */
    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Aggregates usage for a period.
     */
    public void aggregateForPeriod(String period) {
        this.aggregatedPeriod = period;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the usage as invoiced.
     */
    public void markInvoiced(String invoiceId) {
        this.invoiced = true;
        this.invoiceId = invoiceId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getCustomerId() { return customerId; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getMeterId() { return meterId; }
    public Instant getUsageDate() { return usageDate; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public Map<String, String> getMetadata() { return metadata; }
    public String getSource() { return source; }
    public boolean isInvoiced() { return invoiced; }
    public String getInvoiceId() { return invoiceId; }
    public String getAggregatedPeriod() { return aggregatedPeriod; }
    public String getCreatedBy() { return createdBy; }

    public void setSource(String source) {
        this.source = source;
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
        return "UsageRecord{" +
                "id=" + getId() +
                ", customerId='" + customerId + '\'' +
                ", meterId='" + meterId + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", invoiced=" + invoiced +
                '}';
    }

    /**
     * Usage meter configuration.
     */
    public static final class UsageMeter {
        private final String meterId;
        private final String name;
        private final String description;
        private final String unit;
        private final double pricePerUnit;
        private final boolean cumulative;
        private final int aggregationWindowDays;
        private final String currencyCode;

        public UsageMeter(
                String meterId,
                String name,
                String description,
                String unit,
                double pricePerUnit,
                boolean cumulative,
                int aggregationWindowDays,
                String currencyCode) {
            this.meterId = meterId;
            this.name = name;
            this.description = description;
            this.unit = unit;
            this.pricePerUnit = pricePerUnit;
            this.cumulative = cumulative;
            this.aggregationWindowDays = aggregationWindowDays;
            this.currencyCode = currencyCode;
        }

        public String getMeterId() { return meterId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getUnit() { return unit; }
        public double getPricePerUnit() { return pricePerUnit; }
        public boolean isCumulative() { return cumulative; }
        public int getAggregationWindowDays() { return aggregationWindowDays; }
        public String getCurrencyCode() { return currencyCode; }

        public Money calculateCost(double quantity) {
            return Money.of(
                java.math.BigDecimal.valueOf(quantity * pricePerUnit),
                currencyCode
            );
        }
    }
}