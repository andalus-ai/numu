package tech.kayys.numou.billing.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;
import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Billing schedule entity.
 */
@Entity
@Table(name = "billing_schedules")
public class BillingScheduleEntity extends BaseEntity {

    @Column(name = "subscription_id", columnDefinition = "UUID", nullable = false)
    public UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    public String customerId;

    @Column(name = "customer_email", length = 255)
    public String customerEmail;

    @Column(name = "frequency", length = 20, nullable = false)
    public String frequency;

    @Column(name = "status", length = 20, nullable = false)
    public String status;

    @Column(name = "start_date", nullable = false)
    public Instant startDate;

    @Column(name = "end_date")
    public Instant endDate;

    @Column(name = "next_billing_date")
    public Instant nextBillingDate;

    @Column(name = "last_billing_date")
    public Instant lastBillingDate;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    public BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    public String currency;

    @Column(name = "payment_method_token", length = 255)
    public String paymentMethodToken;

    @Column(name = "current_cycle")
    public int currentCycle;

    @Column(name = "total_cycles")
    public int totalCycles;

    @Column(name = "failed_payment_count")
    public int failedPaymentCount;

    @Column(name = "max_failed_payments")
    public int maxFailedPayments = 3;

    @Column(name = "send_email_notifications")
    public boolean sendEmailNotifications = true;

    @Column(name = "send_sms_notifications")
    public boolean sendSmsNotifications = false;

    @Column(name = "active", nullable = false)
    public boolean active;

    public BillingSchedule toDomain() {
        BillingSchedule schedule = BillingSchedule.create(
            tech.kayys.numou.billing.domain.identifier.BillingScheduleId.of(id),
            subscriptionId,
            customerId,
            BillingFrequency.valueOf(frequency),
            tech.kayys.numou.foundation.domain.valueobject.Money.of(amount, currency),
            currency,
            startDate
        );
        schedule.setStatus(BillingStatus.valueOf(status));
        schedule.setCustomerEmail(customerEmail);
        schedule.setEndDate(endDate);
        schedule.setPaymentMethodToken(paymentMethodToken);
        schedule.setTotalCycles(totalCycles);
        schedule.setMaxFailedPayments(maxFailedPayments);
        schedule.setSendEmailNotifications(sendEmailNotifications);
        schedule.setSendSmsNotifications(sendSmsNotifications);
        schedule.setActive(active);
        schedule.restoreProgress(nextBillingDate, lastBillingDate, currentCycle, failedPaymentCount);
        return schedule;
    }

    public static BillingScheduleEntity fromDomain(BillingSchedule schedule) {
        BillingScheduleEntity entity = new BillingScheduleEntity();
        entity.id = schedule.getId().getValue();
        entity.subscriptionId = schedule.getSubscriptionId();
        entity.customerId = schedule.getCustomerId();
        entity.customerEmail = schedule.getCustomerEmail();
        entity.frequency = schedule.getFrequency().name();
        entity.status = schedule.getStatus().name();
        entity.startDate = schedule.getStartDate();
        entity.endDate = schedule.getEndDate();
        entity.nextBillingDate = schedule.getNextBillingDate();
        entity.lastBillingDate = schedule.getLastBillingDate();
        entity.amount = schedule.getAmount().getAmount();
        entity.currency = schedule.getCurrencyCode();
        entity.paymentMethodToken = schedule.getPaymentMethodToken();
        entity.currentCycle = schedule.getCurrentCycle();
        entity.totalCycles = schedule.getTotalCycles();
        entity.failedPaymentCount = schedule.getFailedPaymentCount();
        entity.maxFailedPayments = schedule.getMaxFailedPayments();
        entity.sendEmailNotifications = schedule.isSendEmailNotifications();
        entity.sendSmsNotifications = schedule.isSendSmsNotifications();
        entity.active = schedule.isActive();
        entity.createdAt = schedule.getCreatedAt();
        entity.updatedAt = schedule.getUpdatedAt();
        entity.version = (long) schedule.getVersion();
        return entity;
    }
}