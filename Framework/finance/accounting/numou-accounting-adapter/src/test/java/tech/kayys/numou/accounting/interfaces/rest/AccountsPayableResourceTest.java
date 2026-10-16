package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.ap.ApInvoiceService;
import tech.kayys.syirkah.accounting.payables.infrastructure.InMemoryVendorInvoiceRepository;
import tech.kayys.syirkah.accounting.domain.ap.VendorInvoice;
import tech.kayys.syirkah.accounting.interfaces.rest.dto.RegisterVendorInvoiceRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountsPayableResourceTest {

    private AccountsPayableResource resource;
    private InMemoryVendorInvoiceRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryVendorInvoiceRepository();
        ApInvoiceService apService = new ApInvoiceService(repo);
        resource = new AccountsPayableResource();
        resource.apService = apService;
        resource.vendorRepo = repo;
    }

    @Test
    void testRegisterAndPostVendorInvoice() {
        var line = new RegisterVendorInvoiceRequest.InvoiceLineDto(
                "L1", "6000", "Office Supplies", BigDecimal.valueOf(100), BigDecimal.valueOf(11));
        var req = new RegisterVendorInvoiceRequest(
                "INV-V01", "VEND-01", "PO-99", "USD", List.of(line));

        Response resp = resource.register(req).await().indefinitely();
        assertEquals(201, resp.getStatus());
        VendorInvoice inv = (VendorInvoice) resp.getEntity();
        assertEquals("INV-V01", inv.id().value());

        Response postResp = resource.post("INV-V01", "2000", "1150").await().indefinitely();
        assertEquals(200, postResp.getStatus());

        Response getResp = resource.getById("INV-V01").await().indefinitely();
        assertEquals(200, getResp.getStatus());
    }
}
