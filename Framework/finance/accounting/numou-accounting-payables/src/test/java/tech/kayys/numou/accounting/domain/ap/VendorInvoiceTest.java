package tech.kayys.syirkah.accounting.domain.ap;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VendorInvoiceTest {

    private VendorInvoiceLine line(String acc, String net, String tax) {
        return new VendorInvoiceLine(acc, "test item", new BigDecimal(net), new BigDecimal(tax));
    }

    @Test
    void registers_in_draft_with_one_event() {
        var inv = new VendorInvoice(
                VendorInvoiceId.generate(), VendorId.of("V-1"),
                "INV-001", "IDR", List.of(line("5000", "1000", "110")));

        assertEquals(VendorInvoiceStatus.DRAFT, inv.status());
        assertEquals(1, inv.domainEvents().size());
        assertInstanceOf(VendorInvoiceRegistered.class, inv.domainEvents().get(0));
        assertEquals(new BigDecimal("1110"), inv.totalAmount());
        assertEquals(new BigDecimal("110"), inv.taxAmount());
    }

    @Test
    void posts_and_raises_supplier_invoice_posted() {
        var inv = new VendorInvoice(
                VendorInvoiceId.generate(), VendorId.of("V-1"),
                "INV-001", "IDR", List.of(line("5000", "1000", "110")));
        inv.clearEvents();

        inv.post("2000-AP", "1300-VAT");

        assertEquals(VendorInvoiceStatus.POSTED, inv.status());
        assertEquals(1, inv.domainEvents().size());
        assertInstanceOf(SupplierInvoicePosted.class, inv.domainEvents().get(0));
        var posted = (SupplierInvoicePosted) inv.domainEvents().get(0);
        assertEquals("2000-AP", posted.apAccount());
        assertEquals("1300-VAT", posted.taxAccount());
    }

    @Test
    void cannot_post_twice() {
        var inv = new VendorInvoice(
                VendorInvoiceId.generate(), VendorId.of("V-1"),
                "INV-001", "IDR", List.of(line("5000", "1000", "0")));
        inv.post("2000-AP", null);
        assertThrows(IllegalStateException.class, () -> inv.post("2000-AP", null));
    }

    @Test
    void can_cancel_draft() {
        var inv = new VendorInvoice(
                VendorInvoiceId.generate(), VendorId.of("V-1"),
                "INV-001", "IDR", List.of(line("5000", "1000", "0")));
        inv.cancel();
        assertEquals(VendorInvoiceStatus.CANCELLED, inv.status());
    }

    @Test
    void cannot_cancel_posted() {
        var inv = new VendorInvoice(
                VendorInvoiceId.generate(), VendorId.of("V-1"),
                "INV-001", "IDR", List.of(line("5000", "1000", "0")));
        inv.post("2000-AP", null);
        assertThrows(IllegalStateException.class, inv::cancel);
    }

    @Test
    void rejects_empty_lines() {
        assertThrows(IllegalArgumentException.class, () ->
                new VendorInvoice(VendorInvoiceId.generate(), VendorId.of("V-1"),
                        "INV-001", "IDR", List.of()));
    }
}
