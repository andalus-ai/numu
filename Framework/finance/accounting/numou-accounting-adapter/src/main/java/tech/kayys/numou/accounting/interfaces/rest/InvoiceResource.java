package tech.kayys.syirkah.accounting.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.api.InvoiceService;
import tech.kayys.syirkah.accounting.application.api.command.*;
import tech.kayys.syirkah.accounting.application.api.query.*;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceDeliveryMethod;
import tech.kayys.syirkah.accounting.domain.valueobject.PaymentMethod;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for invoice management.
 */
// Gated: this resource depends on the aspirational application.api.InvoiceService
// interface, which has no implementation. The working invoice endpoints are
// AccountsReceivableResource, backed by receivables.application.InvoiceService
// (produced by AccountingServiceProducer). Remove this gate once
// application.api.InvoiceService is implemented or this resource is retired.
@IfBuildProperty(
        name = "syirkah.accounting.invoice-api.enabled",
        stringValue = "false")
@Path("/api/v1/invoices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Invoice API", description = "Invoice management endpoints")
public class InvoiceResource {

    @Inject
    InvoiceService invoiceService;

    @POST
    @Operation(summary = "Create a new invoice")
    @APIResponses({
        @APIResponse(responseCode = "201", description = "Invoice created"),
        @APIResponse(responseCode = "400", description = "Invalid input")
    })
    public CompletionStage<Response> createInvoice(@Valid CreateInvoiceRequest request) {
        CreateInvoiceCommand command = CreateInvoiceCommand.builder()
            .customerId(request.getCustomerId())
            .invoiceNumber(request.getInvoiceNumber())
            .dueDate(request.getDueDate())
            .customerNotes(request.getCustomerNotes())
            .purchaseOrderNumber(request.getPurchaseOrderNumber())
            .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD")
            .lines(request.getLines().stream()
                .map(line -> new CreateInvoiceCommand.InvoiceLineCommand(
                    line.getProductId(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getTaxRate(),
                    line.getDiscountRate()
                ))
                .collect(java.util.stream.Collectors.toList())
            )
            .build();

        return invoiceService.createInvoice(command)
            .thenApply(invoiceId -> Response
                .created(URI.create("/api/v1/invoices/" + invoiceId.getValue()))
                .entity(new CreateInvoiceResponse(invoiceId))
                .build()
            );
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get invoice by ID")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Invoice found"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    public CompletionStage<Response> getInvoice(@PathParam("id") UUID id) {
        InvoiceId invoiceId = InvoiceId.of(id);
        GetInvoiceQuery query = new GetInvoiceQuery(invoiceId);

        return invoiceService.getInvoice(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/{id}/send")
    @Operation(summary = "Send invoice to customer")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Invoice sent"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    public CompletionStage<Response> sendInvoice(
            @PathParam("id") UUID id,
            @Valid SendInvoiceRequest request) {
        InvoiceId invoiceId = InvoiceId.of(id);

        SendInvoiceCommand command = SendInvoiceCommand.builder()
            .invoiceId(invoiceId)
            .deliveryMethod(request.getDeliveryMethod())
            .emailSubject(request.getEmailSubject())
            .emailBody(request.getEmailBody())
            .templateId(request.getTemplateId())
            .build();

        return invoiceService.sendInvoice(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                if (throwable.getCause() instanceof IllegalStateException) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/{id}/payments")
    @Operation(summary = "Record a payment")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Payment recorded"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    public CompletionStage<Response> recordPayment(
            @PathParam("id") UUID id,
            @Valid RecordPaymentRequest request) {
        InvoiceId invoiceId = InvoiceId.of(id);

        RecordPaymentCommand command = RecordPaymentCommand.builder()
            .invoiceId(invoiceId)
            .amount(request.getAmount())
            .currencyCode(request.getCurrencyCode())
            .paymentMethod(request.getPaymentMethod())
            .reference(request.getReference())
            .transactionId(request.getTransactionId())
            .build();

        return invoiceService.recordPayment(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                if (throwable.getCause() instanceof IllegalStateException) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/{id}/refund")
    @Operation(summary = "Refund an invoice")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Refund processed"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    public CompletionStage<Response> refundInvoice(
            @PathParam("id") UUID id,
            @Valid RefundInvoiceRequest request) {
        InvoiceId invoiceId = InvoiceId.of(id);

        RefundInvoiceCommand command = RefundInvoiceCommand.builder()
            .invoiceId(invoiceId)
            .amount(request.getAmount())
            .currencyCode(request.getCurrencyCode())
            .reason(request.getReason())
            .reference(request.getReference())
            .build();

        return invoiceService.refundInvoice(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/{id}/cancel")
    @Operation(summary = "Cancel an invoice")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Invoice cancelled"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    public CompletionStage<Response> cancelInvoice(
            @PathParam("id") UUID id,
            @Valid CancelInvoiceRequest request) {
        InvoiceId invoiceId = InvoiceId.of(id);

        CancelInvoiceCommand command = CancelInvoiceCommand.builder()
            .invoiceId(invoiceId)
            .reason(request.getReason())
            .build();

        return invoiceService.cancelInvoice(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                if (throwable.getCause() instanceof IllegalStateException) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/{id}/pdf")
    @Operation(summary = "Generate invoice PDF")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "PDF generated"),
        @APIResponse(responseCode = "404", description = "Invoice not found")
    })
    @Produces("application/pdf")
    public CompletionStage<Response> generatePdf(@PathParam("id") UUID id) {
        InvoiceId invoiceId = InvoiceId.of(id);

        GenerateInvoicePdfCommand command = GenerateInvoicePdfCommand.builder()
            .invoiceId(invoiceId)
            .build();

        return invoiceService.generateInvoicePdf(command)
            .thenApply(pdfBytes -> Response
                .ok(pdfBytes)
                .header("Content-Disposition", "attachment; filename=invoice-" + id + ".pdf")
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/search")
    @Operation(summary = "Search invoices")
    @APIResponse(responseCode = "200", description = "Search results")
    public CompletionStage<Response> searchInvoices(
            @QueryParam("customerId") UUID customerId,
            @QueryParam("status") String status,
            @QueryParam("fromDate") String fromDate,
            @QueryParam("toDate") String toDate,
            @QueryParam("minAmount") Double minAmount,
            @QueryParam("maxAmount") Double maxAmount,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @DefaultValue("INVOICE_DATE_DESC") String sort) {
        // Parse and build search query
        SearchInvoicesQuery query = new SearchInvoicesQuery(
            customerId != null ? java.util.UUID.fromString(customerId.toString()) : null,
            status != null ? tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus.valueOf(status) : null,
            fromDate != null ? java.time.Instant.parse(fromDate) : null,
            toDate != null ? java.time.Instant.parse(toDate) : null,
            minAmount,
            maxAmount,
            page,
            size,
            SearchInvoicesQuery.SortBy.valueOf(sort)
        );

        return invoiceService.searchInvoices(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class CreateInvoiceRequest {
        private UUID customerId;
        private String invoiceNumber;
        private Instant dueDate;
        private List<InvoiceLineRequest> lines;
        private String customerNotes;
        private String purchaseOrderNumber;
        private String currencyCode;

        // Getters and setters
        public UUID getCustomerId() { return customerId; }
        public void setCustomerId(UUID customerId) { this.customerId = customerId; }
        public String getInvoiceNumber() { return invoiceNumber; }
        public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
        public Instant getDueDate() { return dueDate; }
        public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
        public List<InvoiceLineRequest> getLines() { return lines; }
        public void setLines(List<InvoiceLineRequest> lines) { this.lines = lines; }
        public String getCustomerNotes() { return customerNotes; }
        public void setCustomerNotes(String customerNotes) { this.customerNotes = customerNotes; }
        public String getPurchaseOrderNumber() { return purchaseOrderNumber; }
        public void setPurchaseOrderNumber(String purchaseOrderNumber) { this.purchaseOrderNumber = purchaseOrderNumber; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    }

    public static class InvoiceLineRequest {
        private UUID productId;
        private String description;
        private int quantity;
        private String unitPrice;
        private String taxRate;
        private String discountRate;

        // Getters and setters
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getUnitPrice() { return unitPrice; }
        public void setUnitPrice(String unitPrice) { this.unitPrice = unitPrice; }
        public String getTaxRate() { return taxRate; }
        public void setTaxRate(String taxRate) { this.taxRate = taxRate; }
        public String getDiscountRate() { return discountRate; }
        public void setDiscountRate(String discountRate) { this.discountRate = discountRate; }
    }

    public static class SendInvoiceRequest {
        private InvoiceDeliveryMethod deliveryMethod;
        private String emailSubject;
        private String emailBody;
        private String templateId;

        // Getters and setters
        public InvoiceDeliveryMethod getDeliveryMethod() { return deliveryMethod; }
        public void setDeliveryMethod(InvoiceDeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }
        public String getEmailSubject() { return emailSubject; }
        public void setEmailSubject(String emailSubject) { this.emailSubject = emailSubject; }
        public String getEmailBody() { return emailBody; }
        public void setEmailBody(String emailBody) { this.emailBody = emailBody; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
    }

    public static class RecordPaymentRequest {
        private String amount;
        private String currencyCode;
        private PaymentMethod paymentMethod;
        private String reference;
        private String transactionId;

        // Getters and setters
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    }

    public static class RefundInvoiceRequest {
        private String amount;
        private String currencyCode;
        private String reason;
        private String reference;

        // Getters and setters
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
    }

    public static class CancelInvoiceRequest {
        private String reason;

        // Getters and setters
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class CreateInvoiceResponse {
        private final InvoiceId invoiceId;

        public CreateInvoiceResponse(InvoiceId invoiceId) {
            this.invoiceId = invoiceId;
        }

        public UUID getInvoiceId() {
            return invoiceId.getValue();
        }
    }
}