package tech.kayys.syirkah.accounting.domain.ar;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomerInvoiceTest {

    private CustomerInvoiceLine line(String acc, String net, String tax) {
        return new CustomerInvoiceLine(acc, "service", new BigDecimal(net), new BigDecimal(tax));
    }

    @Test
    void registers_in_draft() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "50")));

        assertEquals(CustomerInvoiceStatus.DRAFT, inv.status());
        assertEquals(1, inv.domainEvents().size());
        assertInstanceOf(CustomerInvoiceRegistered.class, inv.domainEvents().get(0));
        assertEquals(new BigDecimal("550"), inv.totalAmount());
    }

    @Test
    void posts_and_raises_event() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "50")));
        inv.clearEvents();

        inv.post("1200-AR", "2300-TAX");

        assertEquals(CustomerInvoiceStatus.POSTED, inv.status());
        assertInstanceOf(CustomerInvoicePosted.class, inv.domainEvents().get(0));
    }

    @Test
    void full_payment_transitions_to_paid() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "50")));
        inv.post("1200-AR", "2300-TAX");
        inv.clearEvents();

        inv.receivePayment(CustomerPaymentId.generate(),
                new BigDecimal("550"), "1010-CASH", "1200-AR");

        assertEquals(CustomerInvoiceStatus.PAID, inv.status());
        assertEquals(BigDecimal.ZERO, inv.outstandingBalance());
        assertInstanceOf(CustomerPaymentReceived.class, inv.domainEvents().get(0));
    }

    @Test
    void partial_payment_stays_posted() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "50")));
        inv.post("1200-AR", "2300-TAX");

        inv.receivePayment(CustomerPaymentId.generate(),
                new BigDecimal("300"), "1010-CASH", "1200-AR");

        assertEquals(CustomerInvoiceStatus.POSTED, inv.status());
        assertEquals(new BigDecimal("250"), inv.outstandingBalance());
    }

    @Test
    void ovsyirkahayment_rejected() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "50")));
        inv.post("1200-AR", "2300-TAX");

        assertThrows(IllegalArgumentException.class, () ->
                inv.receivePayment(CustomerPaymentId.generate(),
                        new BigDecimal("9999"), "1010-CASH", "1200-AR"));
    }

    @Test
    void cannot_post_twice() {
        var inv = new CustomerInvoice(
                CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                "CINV-001", "USD", List.of(line("4000-REV", "500", "0")));
        inv.post("1200-AR", null);
        assertThrows(IllegalStateException.class, () -> inv.post("1200-AR", null));
    }

    @Test
    void rejects_empty_lines() {
        assertThrows(IllegalArgumentException.class, () ->
                new CustomerInvoice(CustomerInvoiceId.generate(), CustomerId.of("C-1"),
                        "CINV-001", "USD", List.of()));
    }
}
