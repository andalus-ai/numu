package tech.kayys.syirkah.accounting.receivables;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.application.*;
import tech.kayys.syirkah.accounting.receivables.infrastructure.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceivablesServiceTest {
    @Test
    void invoice_and_payment_are_separate_lifecycles() {
        var invoices = new InMemoryInvoiceRepository();
        var payments = new InMemoryPaymentRepository();
        var invoiceService = new InvoiceService(invoices);
        var paymentService = new PaymentService(invoices, payments);
        var customer = new CustomerId("C-1");
        var invoice = invoiceService.register(new CustomerInvoiceId("INV-1"), customer, "SO-1", "USD",
                List.of(new CustomerInvoiceLine("4000", "Consulting", new BigDecimal("100"), BigDecimal.ZERO)));
        invoiceService.post(invoice.id(), "1100", "2100");
        var payment = paymentService.receive(new CustomerPaymentId("PAY-1"), customer, "USD", new BigDecimal("100"));
        assertEquals(CustomerInvoiceStatus.PAID, paymentService.apply(payment.id(), invoice.id(), "1000", "1100").status());
        assertEquals(CustomerPaymentStatus.APPLIED, payments.find(payment.id()).orElseThrow().status());
    }
}
