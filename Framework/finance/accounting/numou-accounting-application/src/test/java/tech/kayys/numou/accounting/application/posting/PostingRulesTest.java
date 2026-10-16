package tech.kayys.syirkah.accounting.application.posting;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.posting.ap.SupplierInvoicePostedRule;
import tech.kayys.syirkah.accounting.application.posting.ar.*;
import tech.kayys.syirkah.accounting.domain.ap.*;
import tech.kayys.syirkah.accounting.domain.ar.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostingRulesTest {
    @Test
    void supplier_invoice_posts_expense_and_tax_debit_against_payable_credit() {
        var invoice = new VendorInvoice(new VendorInvoiceId("V-1"), new VendorId("SUP-1"),
                "BILL-1", "USD", List.of(new VendorInvoiceLine("6100", "Services",
                        new BigDecimal("100"), new BigDecimal("11"))));
        invoice.post("2100", "2200");
        var instruction = new SupplierInvoicePostedRule().create(
                (SupplierInvoicePosted) invoice.domainEvents().get(1));
        assertEquals(new BigDecimal("111"), instruction.total(PostingLine.Side.DEBIT));
        assertEquals(new BigDecimal("111"), instruction.total(PostingLine.Side.CREDIT));
    }

    @Test
    void customer_invoice_and_payment_rules_balance() {
        var invoice = new CustomerInvoice(new CustomerInvoiceId("I-1"), new CustomerId("C-1"),
                "SO-1", "USD", List.of(new CustomerInvoiceLine("4000", "Services",
                        new BigDecimal("100"), new BigDecimal("11"))));
        invoice.post("1100", "2200");
        var posted = (CustomerInvoicePosted) invoice.domainEvents().get(1);
        var invoicePosting = new CustomerInvoicePostedRule().create(posted);
        assertEquals(invoicePosting.total(PostingLine.Side.DEBIT), invoicePosting.total(PostingLine.Side.CREDIT));
        invoice.receivePayment(new CustomerPaymentId("P-1"), new BigDecimal("111"), "1000", "1100");
        var payment = (CustomerPaymentReceived) invoice.domainEvents().get(2);
        var paymentPosting = new CustomerPaymentReceivedRule().create(payment);
        assertEquals(paymentPosting.total(PostingLine.Side.DEBIT), paymentPosting.total(PostingLine.Side.CREDIT));
    }
}
