package tech.kayys.syirkah.accounting.application.ap;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.ap.*;
import tech.kayys.syirkah.accounting.payables.infrastructure.InMemoryVendorInvoiceRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApInvoiceServiceTest {

    private final ApInvoiceService service =
            new ApInvoiceService(new InMemoryVendorInvoiceRepository());

    private VendorInvoiceLine line(String acc, String net, String tax) {
        return new VendorInvoiceLine(acc, "item", new BigDecimal(net), new BigDecimal(tax));
    }

    @Test
    void register_then_post_full_lifecycle() {
        var id = VendorInvoiceId.generate();
        service.register(id, VendorId.of("V-10"), "INV-500", "IDR",
                List.of(line("5000", "2000", "220")));

        var posted = service.post(id, "2000-AP", "1300-VAT");

        assertEquals(VendorInvoiceStatus.POSTED, posted.status());
        assertEquals(new BigDecimal("2220"), posted.totalAmount());
        assertTrue(posted.domainEvents().stream()
                .anyMatch(e -> e instanceof SupplierInvoicePosted));
    }

    @Test
    void register_then_cancel() {
        var id = VendorInvoiceId.generate();
        service.register(id, VendorId.of("V-10"), "INV-501", "IDR",
                List.of(line("5000", "100", "0")));

        var cancelled = service.cancel(id);
        assertEquals(VendorInvoiceStatus.CANCELLED, cancelled.status());
    }

    @Test
    void find_persists_after_register() {
        var id = VendorInvoiceId.generate();
        service.register(id, VendorId.of("V-11"), "INV-502", "USD",
                List.of(line("5001", "500", "0")));

        assertTrue(service.find(id).isPresent());
    }

    @Test
    void post_unknown_id_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.post(VendorInvoiceId.generate(), "2000-AP", null));
    }
}
