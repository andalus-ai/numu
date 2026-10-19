package tech.kayys.numou.billing.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;
import tech.kayys.numou.billing.domain.model.UsageRecord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
public class UsageRecordEntity extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(name = "subscription_id")
    public String subscriptionId;

    @Column(name = "meter_id", nullable = false)
    public String meterId;

    @Column(name = "usage_date", nullable = false)
    public Instant usageDate;

    @Column(name = "quantity", nullable = false)
    public double quantity;

    @Column(name = "unit", nullable = false)
    public String unit;

    @Column(name = "metadata_json")
    public String metadataJson;

    @Column(name = "source")
    public String source;

    @Column(name = "invoiced", nullable = false)
    public boolean invoiced;

    @Column(name = "invoice_id")
    public String invoiceId;

    @Column(name = "aggregated_period")
    public String aggregatedPeriod;

    @Column(name = "created_by")
    public String createdBy;

    public UsageRecord toDomain() {
        UsageRecord record = UsageRecord.create(
            tech.kayys.numou.billing.domain.identifier.UsageRecordId.of(id),
            customerId,
            subscriptionId,
            meterId,
            quantity,
            unit
        );
        record.setSource(source);
        record.setCreatedBy(createdBy);
        
        // Parse metadata from JSON if present
        // In production, use Jackson to deserialize
        
        if (invoiced) {
            record.markInvoiced(invoiceId);
        }
        if (aggregatedPeriod != null) {
            record.aggregateForPeriod(aggregatedPeriod);
        }
        
        return record;
    }

    public static UsageRecordEntity fromDomain(UsageRecord record) {
        UsageRecordEntity entity = new UsageRecordEntity();
        entity.id = record.getId().getValue();
        entity.customerId = record.getCustomerId();
        entity.subscriptionId = record.getSubscriptionId();
        entity.meterId = record.getMeterId();
        entity.usageDate = record.getUsageDate();
        entity.quantity = record.getQuantity();
        entity.unit = record.getUnit();
        entity.source = record.getSource();
        entity.invoiced = record.isInvoiced();
        entity.invoiceId = record.getInvoiceId();
        entity.aggregatedPeriod = record.getAggregatedPeriod();
        entity.createdBy = record.getCreatedBy();
        entity.createdAt = record.getCreatedAt();
        entity.updatedAt = record.getUpdatedAt();
        entity.version = (long) record.getVersion();
        
        // Serialize metadata to JSON
        // In production, use Jackson to serialize
        
        return entity;
    }
}