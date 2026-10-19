package tech.kayys.numou.crm.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.crm.application.api.CrmService;
import tech.kayys.numou.crm.application.api.command.ConvertLeadCommand;
import tech.kayys.numou.crm.application.api.command.CreateLeadCommand;
import tech.kayys.numou.crm.application.api.query.GetLeadQuery;
import tech.kayys.numou.crm.application.api.query.LeadView;
import tech.kayys.numou.crm.application.api.query.SearchLeadsQuery;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for lead management.
 */
@Path("/api/v1/leads")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Lead API", description = "Lead management endpoints")
public class LeadResource {

    @Inject
    CrmService crmService;

    @POST
    @Operation(summary = "Create a new lead")
    @APIResponse(responseCode = "201", description = "Lead created")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> createLead(@Valid CreateLeadRequest request) {
        CreateLeadCommand command = CreateLeadCommand.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .company(request.getCompany())
            .jobTitle(request.getJobTitle())
            .industry(request.getIndustry())
            .source(request.getSource())
            .notes(request.getNotes())
            .build();

        return crmService.createLead(command)
            .thenApply(leadId -> Response
                .created(URI.create("/api/v1/leads/" + leadId.getValue()))
                .entity(new CreateLeadResponse(leadId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get lead by ID")
    @APIResponse(responseCode = "200", description = "Lead found")
    @APIResponse(responseCode = "404", description = "Lead not found")
    public CompletionStage<Response> getLead(@PathParam("id") UUID id) {
        LeadId leadId = LeadId.of(id);
        GetLeadQuery query = new GetLeadQuery(leadId);

        return crmService.getLead(query)
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
    @Path("/{id}/convert")
    @Operation(summary = "Convert lead to customer")
    @APIResponse(responseCode = "200", description = "Lead converted")
    @APIResponse(responseCode = "400", description = "Invalid conversion")
    public CompletionStage<Response> convertLead(
            @PathParam("id") UUID id,
            @Valid ConvertLeadRequest request) {
        LeadId leadId = LeadId.of(id);

        ConvertLeadCommand command = ConvertLeadCommand.builder()
            .leadId(leadId)
            .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD")
            .paymentTerms(request.getPaymentTerms())
            .creditLimit(request.getCreditLimit())
            .build();

        return crmService.convertLead(command)
            .thenApply(customerId -> Response.ok(new ConvertLeadResponse(customerId)).build())
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
    @Operation(summary = "Search leads")
    public CompletionStage<Response> searchLeads(
            @QueryParam("status") String status,
            @QueryParam("email") String email,
            @QueryParam("company") String company,
            @QueryParam("source") String source,
            @QueryParam("minScore") Double minScore,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        SearchLeadsQuery query = new SearchLeadsQuery(
            status != null ? LeadStatus.valueOf(status) : null,
            email,
            company,
            source,
            minScore,
            page,
            size
        );

        return crmService.searchLeads(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class CreateLeadRequest {
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String company;
        private String jobTitle;
        private String industry;
        private String source;
        private String notes;

        // Getters and setters
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ConvertLeadRequest {
        private String currencyCode;
        private String paymentTerms;
        private String creditLimit;

        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getPaymentTerms() { return paymentTerms; }
        public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
        public String getCreditLimit() { return creditLimit; }
        public void setCreditLimit(String creditLimit) { this.creditLimit = creditLimit; }
    }

    public static class CreateLeadResponse {
        private final String leadId;

        public CreateLeadResponse(LeadId leadId) {
            this.leadId = leadId.toString();
        }

        public String getLeadId() { return leadId; }
    }

    public static class ConvertLeadResponse {
        private final String customerId;

        public ConvertLeadResponse(CustomerId customerId) {
            this.customerId = customerId.toString();
        }

        public String getCustomerId() { return customerId; }
    }
}