package tech.kayys.syirkah.accounting.domain.ar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Payment aggregate applied to a customer invoice by the receivables application service. */
public final class CustomerPayment {
    private final CustomerPaymentId id;
    private final CustomerId customerId;
    private final String currency;
    private final BigDecimal amount;
    private final Instant receivedAt;
    private CustomerPaymentStatus status = CustomerPaymentStatus.PENDING;

    public CustomerPayment(CustomerPaymentId id, CustomerId customerId, String currency,
                           BigDecimal amount, Instant receivedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("payment amount must be positive");
        this.currency = currency;
        this.amount = amount;
        this.receivedAt = Objects.requireNonNull(receivedAt);
    }
    public CustomerPaymentId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public String currency() { return currency; }
    public BigDecimal amount() { return amount; }
    public Instant receivedAt() { return receivedAt; }
    public CustomerPaymentStatus status() { return status; }
    public void markApplied() {
        if (status != CustomerPaymentStatus.PENDING) throw new IllegalStateException("payment is already " + status);
        status = CustomerPaymentStatus.APPLIED;
    }
    public void reverse() {
        if (status != CustomerPaymentStatus.APPLIED) throw new IllegalStateException("only applied payments can be reversed");
        status = CustomerPaymentStatus.REVERSED;
    }
}
