package tech.kayys.numou.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One billed cycle of a billing schedule.
 */
@Entity
@Table(name = "billing_cycles")
public class BillingCycleEntity extends BaseEntity {

    @Column(name = "schedule_id", columnDefinition = "UUID", nullable = false)
    public UUID scheduleId;

    @Column(name = "cycle_number", nullable = false)
    public int cycleNumber;

    @Column(name = "billing_date", nullable = false)
    public Instant billingDate;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    public BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    public String currency;

    @Column(name = "invoice_id")
    public String invoiceId;

    @Column(name = "status", length = 20, nullable = false)
    public String status;

    @Column(name = "transaction_id")
    public String transactionId;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "processed_at")
    public Instant processedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    public static BillingCycleEntity fromDomain(UUID scheduleId, BillingSchedule.BillingCycle c) {
        BillingCycleEntity e = new BillingCycleEntity();
        e.id = UUID.randomUUID();
        e.scheduleId = scheduleId;
        e.copyFrom(c);
        return e;
    }

    /** Copies the cycle's current state onto this row. */
    public void copyFrom(BillingSchedule.BillingCycle c) {
        cycleNumber = c.getCycleNumber();
        billingDate = c.getBillingDate();
        amount = c.getAmount().getAmount();
        currency = c.getCurrencyCode();
        invoiceId = c.getInvoiceId();
        status = c.getStatus().name();
        transactionId = c.getTransactionId();
        errorMessage = c.getErrorMessage();
        processedAt = c.getProcessedAt();
        completedAt = c.getCompletedAt();
        updatedAt = Instant.now();
    }

    public BillingSchedule.BillingCycle toDomain() {
        return BillingSchedule.BillingCycle.restore(cycleNumber, billingDate, Money.of(amount, currency), currency,
            invoiceId, BillingSchedule.BillingCycleStatus.valueOf(status), transactionId, errorMessage, processedAt,
            completedAt);
    }
}
