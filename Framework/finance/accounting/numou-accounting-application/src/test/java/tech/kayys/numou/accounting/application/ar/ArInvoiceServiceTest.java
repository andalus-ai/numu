package tech.kayys.syirkah.accounting.application.ar;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.receivables.infrastructure.InMemoryCustomerInvoiceRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArInvoiceServiceTest {

    private final ArInvoiceService service =
            new ArInvoiceService(new InMemoryCustomerInvoiceRepository());

    private CustomerInvoiceLine line(String acc, String net, String tax) {
        return new CustomerInvoiceLine(acc, "service", new BigDecimal(net), new BigDecimal(tax));
    }

    @Test
    void register_post_and_full_payment() {
        var id = CustomerInvoiceId.generate();
        service.register(id, CustomerId.of("C-42"), "CINV-100", "USD",
                List.of(line("4000-REV", "1000", "100")));

        service.post(id, "1200-AR", "2300-TAX");

        var paid = service.receivePayment(id, CustomerPaymentId.generate(),
                new BigDecimal("1100"), "1010-CASH", "1200-AR");

        assertEquals(CustomerInvoiceStatus.PAID, paid.status());
        assertEquals(BigDecimal.ZERO, paid.outstandingBalance());
    }

    @Test
    void partial_payment_keeps_invoice_posted() {
        var id = CustomerInvoiceId.generate();
        service.register(id, CustomerId.of("C-43"), "CINV-101", "USD",
                List.of(line("4000-REV", "1000", "0")));
        service.post(id, "1200-AR", null);

        var inv = service.receivePayment(id, CustomerPaymentId.generate(),
                new BigDecimal("600"), "1010-CASH", "1200-AR");

        assertEquals(CustomerInvoiceStatus.POSTED, inv.status());
        assertEquals(new BigDecimal("400"), inv.outstandingBalance());
    }

    @Test
    void cancel_draft_invoice() {
        var id = CustomerInvoiceId.generate();
        service.register(id, CustomerId.of("C-44"), "CINV-102", "USD",
                List.of(line("4000-REV", "200", "0")));

        var cancelled = service.cancel(id);
        assertEquals(CustomerInvoiceStatus.CANCELLED, cancelled.status());
    }

    @Test
    void cannot_pay_unposted_invoice() {
        var id = CustomerInvoiceId.generate();
        service.register(id, CustomerId.of("C-45"), "CINV-103", "USD",
                List.of(line("4000-REV", "500", "0")));

        assertThrows(IllegalStateException.class, () ->
                service.receivePayment(id, CustomerPaymentId.generate(),
                        new BigDecimal("500"), "1010-CASH", "1200-AR"));
    }
}
