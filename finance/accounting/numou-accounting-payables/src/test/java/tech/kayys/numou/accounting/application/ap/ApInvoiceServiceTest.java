package tech.kayys.numou.accounting.application.ap;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.ap.*;
import tech.kayys.numou.accounting.payables.infrastructure.InMemoryVendorInvoiceRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApInvoiceServiceTest {
    private final AtomicReference<ApRequestContext> current =
            new AtomicReference<>(new ApRequestContext("tenant-a", "actor-a"));
    private final InMemoryVendorInvoiceRepository repository = new InMemoryVendorInvoiceRepository();
    private final ApInvoiceService service = new ApInvoiceService(repository, current::get,
            (actor, scope, operation) -> {
                if (!actor.tenantId().equals(scope.tenantId())) throw new SecurityException("tenant mismatch");
            });

    private VendorInvoiceLine line(String acc, String net, String tax) {
        return new VendorInvoiceLine(acc, "item", new BigDecimal(net), new BigDecimal(tax));
    }

    @Test
    void register_then_post_full_lifecycle() {
        var id = VendorInvoiceId.generate();
        service.register(id, "company-a", "ledger-a", "period-a", VendorId.of("V-10"), "INV-500", "IDR",
                List.of(line("5000", "2000", "220")));
        var posted = service.post(id, "2000-AP", "1300-VAT");
        assertEquals(VendorInvoiceStatus.POSTED, posted.status());
        assertEquals(new BigDecimal("2220"), posted.totalAmount());
        assertTrue(posted.domainEvents().stream().anyMatch(e -> e instanceof SupplierInvoicePosted));
    }

    @Test
    void same_invoice_id_is_isolated_between_tenants() {
        var id = VendorInvoiceId.of("same-id");
        service.register(id, "company-a", "ledger-a", "period-a", VendorId.of("V-A"), "A-1", "IDR",
                List.of(line("5000", "100", "0")));
        current.set(new ApRequestContext("tenant-b", "actor-b"));
        service.register(id, "company-b", "ledger-b", "period-b", VendorId.of("V-B"), "B-1", "IDR",
                List.of(line("5000", "200", "0")));
        assertEquals("A-1", repository.find("tenant-a", id).orElseThrow().vendorRef());
        assertEquals("B-1", service.find(id).orElseThrow().vendorRef());
        current.set(new ApRequestContext("tenant-c", "actor-c"));
        assertTrue(service.find(id).isEmpty());
    }

    @Test
    void authorization_is_checked_before_mutation() {
        var id = VendorInvoiceId.generate();
        service.register(id, "company-a", "ledger-a", "period-a", VendorId.of("V-10"), "INV-501", "IDR",
                List.of(line("5000", "100", "0")));
        ApInvoiceService denied = new ApInvoiceService(repository, current::get,
                (actor, scope, operation) -> { throw new SecurityException("denied"); });
        assertThrows(SecurityException.class, () -> denied.post(id, "2000-AP", null));
        assertEquals(VendorInvoiceStatus.DRAFT, repository.find("tenant-a", id).orElseThrow().status());
    }

    @Test
    void tenant_context_is_required_for_every_operation() {
        ApInvoiceService missingContext = new ApInvoiceService(repository, () -> {
            throw new SecurityException("verified tenant required");
        }, (actor, scope, operation) -> {});
        assertThrows(SecurityException.class, () -> missingContext.find(VendorInvoiceId.generate()));
    }

    @Test
    void cancel_draft() {
        var id = VendorInvoiceId.generate();
        service.register(id, "company-a", "ledger-a", "period-a", VendorId.of("V-10"), "INV-502", "IDR",
                List.of(line("5000", "100", "0")));
        assertEquals(VendorInvoiceStatus.CANCELLED, service.cancel(id).status());
    }
}
