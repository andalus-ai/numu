package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.ar.ArInvoiceService;
import tech.kayys.syirkah.accounting.domain.ar.CustomerInvoice;
import tech.kayys.syirkah.accounting.receivables.infrastructure.InMemoryCustomerInvoiceRepository;
import tech.kayys.syirkah.accounting.interfaces.rest.dto.ReceiveCustomerPaymentRequest;
import tech.kayys.syirkah.accounting.interfaces.rest.dto.RegisterCustomerInvoiceRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountsReceivableResourceTest {

    private AccountsReceivableResource resource;
    private InMemoryCustomerInvoiceRepository invRepo;

    @BeforeEach
    void setUp() {
        invRepo = new InMemoryCustomerInvoiceRepository();
        ArInvoiceService arService = new ArInvoiceService(invRepo);
        resource = new AccountsReceivableResource();
        resource.arService = arService;
        resource.invoiceRepo = invRepo;
    }

    @Test
    void testRegisterPostAndPayment() {
        var line = new RegisterCustomerInvoiceRequest.InvoiceLineDto(
                "L1", "4000", "Consulting", BigDecimal.valueOf(1000), BigDecimal.valueOf(110));
        var req = new RegisterCustomerInvoiceRequest(
                "INV-C01", "CUST-01", "SO-123", "USD", List.of(line));

        Response resp = resource.register(req).await().indefinitely();
        assertEquals(201, resp.getStatus());
        CustomerInvoice inv = (CustomerInvoice) resp.getEntity();
        assertEquals("INV-C01", inv.id().value());

        Response postResp = resource.post("INV-C01", "1200", "2150").await().indefinitely();
        assertEquals(200, postResp.getStatus());

        var payReq = new ReceiveCustomerPaymentRequest(
                "PAY-01", "CUST-01", "INV-C01", BigDecimal.valueOf(1110), "USD", "1010", "1200");
        Response payResp = resource.receivePayment(payReq).await().indefinitely();
        assertEquals(201, payResp.getStatus());
        CustomerInvoice paidInvoice = (CustomerInvoice) payResp.getEntity();
        assertEquals("INV-C01", paidInvoice.id().value());
    }
}
