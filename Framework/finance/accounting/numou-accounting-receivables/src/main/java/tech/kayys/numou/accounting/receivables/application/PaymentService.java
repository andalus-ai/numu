package tech.kayys.syirkah.accounting.receivables.application;

import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class PaymentService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final Clock clock;
    public PaymentService(InvoiceRepository invoices, PaymentRepository payments) {
        this(invoices, payments, Clock.systemUTC());
    }
    public PaymentService(InvoiceRepository invoices, PaymentRepository payments, Clock clock) {
        this.invoices = Objects.requireNonNull(invoices);
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }
    public CustomerPayment receive(CustomerPaymentId id, CustomerId customerId, String currency, BigDecimal amount) {
        if (payments.find(id).isPresent()) throw new IllegalStateException("payment already exists: " + id.value());
        return payments.save(new CustomerPayment(id, customerId, currency, amount, Instant.now(clock)));
    }
    public CustomerInvoice apply(CustomerPaymentId paymentId, CustomerInvoiceId invoiceId,
                                 String cashAccount, String arAccount) {
        var payment = payments.find(paymentId)
                .orElseThrow(() -> new java.util.NoSuchElementException("unknown payment: " + paymentId.value()));
        var invoice = invoices.find(invoiceId)
                .orElseThrow(() -> new java.util.NoSuchElementException("unknown invoice: " + invoiceId.value()));
        if (!payment.customerId().equals(invoice.customerId()) || !payment.currency().equals(invoice.currency())) {
            throw new IllegalArgumentException("payment customer and currency must match invoice");
        }
        invoice.receivePayment(payment.id(), payment.amount(), cashAccount, arAccount);
        payment.markApplied();
        invoices.save(invoice);
        payments.save(payment);
        return invoice;
    }
}
