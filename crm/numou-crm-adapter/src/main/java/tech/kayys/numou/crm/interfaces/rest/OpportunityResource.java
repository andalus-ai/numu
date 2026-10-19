package tech.kayys.numou.crm.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.crm.application.api.CrmService;
import tech.kayys.numou.crm.application.api.command.CreateOpportunityCommand;
import tech.kayys.numou.crm.application.api.command.MoveOpportunityStageCommand;
import tech.kayys.numou.crm.application.api.command.UpdateOpportunityCommand;
import tech.kayys.numou.crm.application.api.query.GetOpportunityQuery;
import tech.kayys.numou.crm.application.api.query.OpportunityView;
import tech.kayys.numou.crm.application.api.query.SearchOpportunitiesQuery;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.identity.domain.user.UserId;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for opportunity management.
 */
@Path("/api/v1/opportunities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Opportunity API", description = "Opportunity management endpoints")
public class OpportunityResource {

    @Inject
    CrmService crmService;

    private static UserId toUserId(String value) {
        return value == null || value.isBlank() ? null : UserId.of(UUID.fromString(value));
    }

    @POST
    @Operation(summary = "Create a new opportunity")
    @APIResponse(responseCode = "201", description = "Opportunity created")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> createOpportunity(@Valid CreateOpportunityRequest request) {
        CreateOpportunityCommand command = CreateOpportunityCommand.builder()
            .name(request.getName())
            .description(request.getDescription())
            .customerId(request.getCustomerId())
            .customerName(request.getCustomerName())
            .stage(request.getStage() != null ? request.getStage() : OpportunityStage.PROSPECTING)
            .estimatedValue(request.getEstimatedValue())
            .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD")
            .assignedTo(toUserId(request.getAssignedTo()))
            .expectedCloseDate(request.getExpectedCloseDate())
            .leadSource(request.getLeadSource())
            .productInterest(request.getProductInterest())
            .notes(request.getNotes())
            .build();

        return crmService.createOpportunity(command)
            .thenApply(opportunityId -> Response
                .created(URI.create("/api/v1/opportunities/" + opportunityId.getValue()))
                .entity(new CreateOpportunityResponse(opportunityId))
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
    @Operation(summary = "Get opportunity by ID")
    @APIResponse(responseCode = "200", description = "Opportunity found")
    @APIResponse(responseCode = "404", description = "Opportunity not found")
    public CompletionStage<Response> getOpportunity(@PathParam("id") UUID id) {
        OpportunityId opportunityId = OpportunityId.of(id);
        GetOpportunityQuery query = new GetOpportunityQuery(opportunityId);

        return crmService.getOpportunity(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an opportunity")
    @APIResponse(responseCode = "200", description = "Opportunity updated")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "404", description = "Opportunity not found")
    public CompletionStage<Response> updateOpportunity(
            @PathParam("id") UUID id,
            @Valid UpdateOpportunityRequest request) {
        OpportunityId opportunityId = OpportunityId.of(id);

        UpdateOpportunityCommand command = UpdateOpportunityCommand.builder()
            .opportunityId(opportunityId)
            .name(request.getName())
            .description(request.getDescription())
            .estimatedValue(request.getEstimatedValue())
            .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD")
            .assignedTo(toUserId(request.getAssignedTo()))
            .expectedCloseDate(request.getExpectedCloseDate())
            .leadSource(request.getLeadSource())
            .productInterest(request.getProductInterest())
            .competitors(request.getCompetitors())
            .decisionCriteria(request.getDecisionCriteria())
            .nextStep(request.getNextStep())
            .notes(request.getNotes())
            .build();

        return crmService.updateOpportunity(command)
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
    @Path("/{id}/stage")
    @Operation(summary = "Move opportunity to a new stage")
    @APIResponse(responseCode = "200", description = "Stage updated")
    @APIResponse(responseCode = "400", description = "Invalid stage transition")
    @APIResponse(responseCode = "404", description = "Opportunity not found")
    public CompletionStage<Response> moveStage(
            @PathParam("id") UUID id,
            @Valid MoveStageRequest request) {
        OpportunityId opportunityId = OpportunityId.of(id);

        MoveOpportunityStageCommand command = MoveOpportunityStageCommand.builder()
            .opportunityId(opportunityId)
            .newStage(request.getNewStage())
            .reason(request.getReason())
            .build();

        return crmService.moveOpportunityStage(command)
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
    @Path("/pipeline")
    @Operation(summary = "Get sales pipeline")
    @APIResponse(responseCode = "200", description = "Pipeline data")
    public CompletionStage<Response> getPipeline(
            @QueryParam("assignedTo") String assignedTo,
            @QueryParam("customerId") UUID customerId) {
        return crmService.getPipeline(assignedTo, customerId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @GET
    @Operation(summary = "Search opportunities")
    @APIResponse(responseCode = "200", description = "Search results")
    public CompletionStage<Response> searchOpportunities(
            @QueryParam("customerId") UUID customerId,
            @QueryParam("stage") String stage,
            @QueryParam("assignedTo") String assignedTo,
            @QueryParam("minValue") Double minValue,
            @QueryParam("maxValue") Double maxValue,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        SearchOpportunitiesQuery query = new SearchOpportunitiesQuery(
            customerId,
            stage != null ? OpportunityStage.valueOf(stage) : null,
            assignedTo,
            minValue,
            maxValue,
            page,
            size
        );

        return crmService.searchOpportunities(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class CreateOpportunityRequest {
        private String name;
        private String description;
        private UUID customerId;
        private String customerName;
        private OpportunityStage stage;
        private double estimatedValue;
        private String currencyCode;
        private String assignedTo;
        private Instant expectedCloseDate;
        private String leadSource;
        private String productInterest;
        private String notes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public UUID getCustomerId() { return customerId; }
        public void setCustomerId(UUID customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public OpportunityStage getStage() { return stage; }
        public void setStage(OpportunityStage stage) { this.stage = stage; }
        public double getEstimatedValue() { return estimatedValue; }
        public void setEstimatedValue(double estimatedValue) { this.estimatedValue = estimatedValue; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public Instant getExpectedCloseDate() { return expectedCloseDate; }
        public void setExpectedCloseDate(Instant expectedCloseDate) { this.expectedCloseDate = expectedCloseDate; }
        public String getLeadSource() { return leadSource; }
        public void setLeadSource(String leadSource) { this.leadSource = leadSource; }
        public String getProductInterest() { return productInterest; }
        public void setProductInterest(String productInterest) { this.productInterest = productInterest; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class UpdateOpportunityRequest {
        private String name;
        private String description;
        private double estimatedValue;
        private String currencyCode;
        private String assignedTo;
        private Instant expectedCloseDate;
        private String leadSource;
        private String productInterest;
        private String competitors;
        private String decisionCriteria;
        private String nextStep;
        private String notes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getEstimatedValue() { return estimatedValue; }
        public void setEstimatedValue(double estimatedValue) { this.estimatedValue = estimatedValue; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public Instant getExpectedCloseDate() { return expectedCloseDate; }
        public void setExpectedCloseDate(Instant expectedCloseDate) { this.expectedCloseDate = expectedCloseDate; }
        public String getLeadSource() { return leadSource; }
        public void setLeadSource(String leadSource) { this.leadSource = leadSource; }
        public String getProductInterest() { return productInterest; }
        public void setProductInterest(String productInterest) { this.productInterest = productInterest; }
        public String getCompetitors() { return competitors; }
        public void setCompetitors(String competitors) { this.competitors = competitors; }
        public String getDecisionCriteria() { return decisionCriteria; }
        public void setDecisionCriteria(String decisionCriteria) { this.decisionCriteria = decisionCriteria; }
        public String getNextStep() { return nextStep; }
        public void setNextStep(String nextStep) { this.nextStep = nextStep; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class MoveStageRequest {
        private OpportunityStage newStage;
        private String reason;

        public OpportunityStage getNewStage() { return newStage; }
        public void setNewStage(OpportunityStage newStage) { this.newStage = newStage; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class CreateOpportunityResponse {
        private final String opportunityId;

        public CreateOpportunityResponse(OpportunityId opportunityId) {
            this.opportunityId = opportunityId.toString();
        }

        public String getOpportunityId() { return opportunityId; }
    }
}