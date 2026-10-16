package tech.kayys.syirkah.accounting.domain.ar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Raised when a customer payment is applied against a CustomerInvoice.
 * Posting rule: DR Cash / CR Accounts Receivable.
 */
public record CustomerPaymentReceived(
        CustomerPaymentId paymentId,
        CustomerInvoiceId invoiceId,
        CustomerId customerId,
        BigDecimal amount,
        String currency,
        String cashAccount,
        String arAccount,
        Instant occurredAt
) {
    public CustomerPaymentReceived {
        Objects.requireNonNull(paymentId); Objects.requireNonNull(invoiceId);
        Objects.requireNonNull(customerId); Objects.requireNonNull(amount);
        Objects.requireNonNull(currency); Objects.requireNonNull(cashAccount);
        Objects.requireNonNull(arAccount); Objects.requireNonNull(occurredAt);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
    }
}
