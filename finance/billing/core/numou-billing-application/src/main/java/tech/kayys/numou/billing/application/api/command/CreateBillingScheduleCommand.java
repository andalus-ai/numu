package tech.kayys.numou.billing.application.api.command;

import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.foundation.application.command.Command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateBillingScheduleCommand(
        BillingScheduleId billingScheduleId,
        UUID subscriptionId,
        String customerId,
        String customerEmail,
        BillingFrequency frequency,
        BigDecimal amount,
        String currencyCode,
        Instant startDate,
        Integer totalCycles,
        Integer maxFailedPayments,
        String paymentMethodToken,
        boolean sendEmailNotifications,
        boolean sendSmsNotifications,
        String createdBy
) implements Command {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private BillingScheduleId billingScheduleId;
        private UUID subscriptionId;
        private String customerId;
        private String customerEmail;
        private BillingFrequency frequency;
        private BigDecimal amount;
        private String currencyCode;
        private Instant startDate;
        private Integer totalCycles;
        private Integer maxFailedPayments;
        private String paymentMethodToken;
        private boolean sendEmailNotifications;
        private boolean sendSmsNotifications;
        private String createdBy;

        public Builder billingScheduleId(BillingScheduleId billingScheduleId) { this.billingScheduleId = billingScheduleId; return this; }
        public Builder subscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; return this; }
        public Builder customerId(String customerId) { this.customerId = customerId; return this; }
        public Builder customerEmail(String customerEmail) { this.customerEmail = customerEmail; return this; }
        public Builder frequency(BillingFrequency frequency) { this.frequency = frequency; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder currencyCode(String currencyCode) { this.currencyCode = currencyCode; return this; }
        public Builder startDate(Instant startDate) { this.startDate = startDate; return this; }
        public Builder totalCycles(Integer totalCycles) { this.totalCycles = totalCycles; return this; }
        public Builder maxFailedPayments(Integer maxFailedPayments) { this.maxFailedPayments = maxFailedPayments; return this; }
        public Builder paymentMethodToken(String paymentMethodToken) { this.paymentMethodToken = paymentMethodToken; return this; }
        public Builder sendEmailNotifications(boolean sendEmailNotifications) { this.sendEmailNotifications = sendEmailNotifications; return this; }
        public Builder sendSmsNotifications(boolean sendSmsNotifications) { this.sendSmsNotifications = sendSmsNotifications; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        public CreateBillingScheduleCommand build() {
            return new CreateBillingScheduleCommand(billingScheduleId, subscriptionId, customerId, customerEmail, frequency, amount, currencyCode, startDate, totalCycles, maxFailedPayments, paymentMethodToken, sendEmailNotifications, sendSmsNotifications, createdBy);
        }
    }
}
