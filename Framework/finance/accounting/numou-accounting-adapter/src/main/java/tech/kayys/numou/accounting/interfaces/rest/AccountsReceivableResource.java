package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.ar.ArInvoiceService;
import tech.kayys.syirkah.accounting.receivables.application.InvoiceService;
import tech.kayys.syirkah.accounting.receivables.application.PaymentService;
import tech.kayys.syirkah.accounting.receivables.infrastructure.InMemoryCustomerInvoiceRepository;
import tech.kayys.syirkah.accounting.domain.ar.*;
import tech.kayys.syirkah.accounting.interfaces.rest.dto.ReceiveCustomerPaymentRequest;
import tech.kayys.syirkah.accounting.interfaces.rest.dto.RegisterCustomerInvoiceRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Path("/api/v1/accounting/ar/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts Receivable API", description = "Customer invoice registration, posting, and payments")
public class AccountsReceivableResource {

    @Inject
    ArInvoiceService arService;

    @Inject
    InvoiceService invoiceService;

    @Inject
    PaymentService paymentService;

    @Inject
    InMemoryCustomerInvoiceRepository invoiceRepo;

    @POST
    @Path("/register")
    @Operation(summary = "Register a new customer invoice")
    public Uni<Response> register(RegisterCustomerInvoiceRequest req) {
        return Uni.createFrom().item(() -> {
            List<CustomerInvoiceLine> lines = new ArrayList<>();
            if (req.lines() != null) {
                for (RegisterCustomerInvoiceRequest.InvoiceLineDto l : req.lines()) {
                    lines.add(new CustomerInvoiceLine(l.accountCode(), l.description(), l.netAmount(), l.taxAmount()));
                }
            }
            CustomerInvoice invoice = invoiceService != null
                    ? invoiceService.register(new CustomerInvoiceId(req.invoiceId()),
                        new CustomerId(req.customerId()), req.customerRef(), req.currency(), lines)
                    : arService.register(new CustomerInvoiceId(req.invoiceId()),
                        new CustomerId(req.customerId()), req.customerRef(), req.currency(), lines);
            return Response.created(URI.create("/api/v1/accounting/ar/invoices/" + invoice.id().value()))
                    .entity(invoice)
                    .build();
        });
    }

    @POST
    @Path("/{id}/post")
    @Operation(summary = "Post a customer invoice to AR subledger")
    public Uni<Response> post(@PathParam("id") String id, @QueryParam("arAccount") String arAccount, @QueryParam("taxAccount") String taxAccount) {
        return Uni.createFrom().item(() -> {
            CustomerInvoice invoice = invoiceService != null
                    ? invoiceService.post(new CustomerInvoiceId(id), arAccount, taxAccount)
                    : arService.post(new CustomerInvoiceId(id), arAccount, taxAccount);
            return Response.ok(invoice).build();
        });
    }

    @POST
    @Path("/payments")
    @Operation(summary = "Receive and apply customer payment")
    public Uni<Response> receivePayment(ReceiveCustomerPaymentRequest req) {
        return Uni.createFrom().item(() -> {
            CustomerInvoice invoice;
            if (paymentService != null) {
                var paymentId = new CustomerPaymentId(req.paymentId());
                paymentService.receive(paymentId, new CustomerId(req.customerId()), req.currency(), req.amount());
                invoice = paymentService.apply(paymentId, new CustomerInvoiceId(req.invoiceId()),
                        req.cashAccount(), req.arAccount());
            } else {
                invoice = arService.receivePayment(new CustomerInvoiceId(req.invoiceId()),
                        new CustomerPaymentId(req.paymentId()), req.amount(), req.cashAccount(), req.arAccount());
            }
            return Response.status(Response.Status.CREATED).entity(invoice).build();
        });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get customer invoice by ID")
    public Uni<Response> getById(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> {
            var invoiceId = new CustomerInvoiceId(id);
            var invoice = invoiceService != null
                    ? invoiceService.find(invoiceId)
                    : invoiceRepo.find(invoiceId);
            return invoice.map(value -> Response.ok(value).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
        });
    }
}
