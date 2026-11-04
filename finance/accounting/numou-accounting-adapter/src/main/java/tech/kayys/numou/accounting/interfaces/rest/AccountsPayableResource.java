package tech.kayys.numou.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.accounting.application.ap.ApInvoiceService;
import tech.kayys.numou.accounting.domain.ap.VendorId;
import tech.kayys.numou.accounting.domain.ap.VendorInvoice;
import tech.kayys.numou.accounting.domain.ap.VendorInvoiceId;
import tech.kayys.numou.accounting.domain.ap.VendorInvoiceLine;
import tech.kayys.numou.accounting.interfaces.rest.dto.RegisterVendorInvoiceRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Path("/api/v1/accounting/ap/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Accounts Payable API", description = "Vendor invoice registration and posting")
public class AccountsPayableResource {
    @Inject ApInvoiceService apService;

    @POST
    @Path("/register")
    @Operation(summary = "Register a new vendor invoice")
    public Uni<Response> register(RegisterVendorInvoiceRequest req) {
        return Uni.createFrom().item(() -> {
            List<VendorInvoiceLine> lines = new ArrayList<>();
            if (req.lines() != null) {
                for (RegisterVendorInvoiceRequest.InvoiceLineDto line : req.lines()) {
                    lines.add(new VendorInvoiceLine(line.accountCode(), line.description(), line.netAmount(), line.taxAmount()));
                }
            }
            VendorInvoice invoice = apService.register(new VendorInvoiceId(req.invoiceId()),
                    req.companyId(), req.ledgerId(), req.periodId(), new VendorId(req.vendorId()),
                    req.vendorRef(), req.currency(), lines);
            return Response.created(URI.create("/api/v1/accounting/ap/invoices/" + invoice.id().value()))
                    .entity(invoice).build();
        });
    }

    @POST
    @Path("/{id}/post")
    @Operation(summary = "Post a vendor invoice to AP subledger")
    public Uni<Response> post(@PathParam("id") String id, @QueryParam("apAccount") String apAccount,
                              @QueryParam("taxAccount") String taxAccount) {
        return Uni.createFrom().item(() -> {
            VendorInvoice invoice = apService.post(new VendorInvoiceId(id), apAccount, taxAccount);
            return Response.ok(invoice).build();
        });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get vendor invoice by ID")
    public Uni<Response> getById(@PathParam("id") String id) {
        return Uni.createFrom().item(() -> apService.find(new VendorInvoiceId(id))
                .map(invoice -> Response.ok(invoice).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()));
    }
}
