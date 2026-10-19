package tech.kayys.numou.billing.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.billing.application.api.BillingService;
import tech.kayys.numou.billing.application.api.command.*;
import tech.kayys.numou.billing.application.api.query.*;
import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.billing.domain.valueobject.*;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for billing operations.
 */
@Path("/api/v1/billing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Billing API", description = "Billing and recurring payment operations")
public class BillingResource {

    @Inject
    BillingService billingService;

    // ============ Billing Schedule Endpoints ============

    @POST
    @Path("/schedules")
    @Operation(summary = "Create a billing schedule")
    public CompletionStage<Response> createBillingSchedule(@Valid CreateBillingScheduleRequest request) {
        CreateBillingScheduleCommand command = CreateBillingScheduleCommand.builder()
            .subscriptionId(request.getSubscriptionId())
            .customerId(request.getCustomerId())
            .customerEmail(request.getCustomerEmail())
            .frequency(request.getFrequency())
            .amount(request.getAmount())
            .currencyCode(request.getCurrencyCode())
            .startDate(request.getStartDate())
            .totalCycles(request.getTotalCycles())
            .paymentMethodToken(request.getPaymentMethodToken())
            .build();

        return billingService.createBillingSchedule(command)
            .thenApply(scheduleId -> Response
                .created(URI.create("/api/v1/billing/schedules/" + scheduleId.getValue()))
                .entity(new CreateBillingScheduleResponse(scheduleId))
                .build()
            );
    }

    @POST
    @Path("/schedules/{id}/activate")
    @Operation(summary = "Activate a billing schedule")
    public CompletionStage<Response> activateBillingSchedule(@PathParam("id") UUID id) {
        BillingScheduleId scheduleId = BillingScheduleId.of(id);
        ActivateBillingScheduleCommand command = new ActivateBillingScheduleCommand(scheduleId);
        return billingService.activateBillingSchedule(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/schedules/{id}/pause")
    @Operation(summary = "Pause a billing schedule")
    public CompletionStage<Response> pauseBillingSchedule(@PathParam("id") UUID id) {
        BillingScheduleId scheduleId = BillingScheduleId.of(id);
        PauseBillingScheduleCommand command = new PauseBillingScheduleCommand(scheduleId);
        return billingService.pauseBillingSchedule(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/schedules/{id}/cancel")
    @Operation(summary = "Cancel a billing schedule")
    public CompletionStage<Response> cancelBillingSchedule(
            @PathParam("id") UUID id,
            @Valid CancelBillingScheduleRequest request) {
        BillingScheduleId scheduleId = BillingScheduleId.of(id);
        CancelBillingScheduleCommand command = new CancelBillingScheduleCommand(
            scheduleId,
            request.getReason()
        );
        return billingService.cancelBillingSchedule(command)
            .thenApply(response -> Response.ok().build());
    }

    @GET
    @Path("/schedules/{id}")
    @Operation(summary = "Get billing schedule")
    public CompletionStage<Response> getBillingSchedule(@PathParam("id") UUID id) {
        BillingScheduleId scheduleId = BillingScheduleId.of(id);
        return billingService.getBillingSchedule(scheduleId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build)
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @GET
    @Path("/schedules/by-subscription/{subscriptionId}")
    @Operation(summary = "Get billing schedule by subscription")
    public CompletionStage<Response> getBillingScheduleBySubscription(
            @PathParam("subscriptionId") UUID subscriptionId) {
        return billingService.getBillingScheduleBySubscription(subscriptionId)
            .thenApply(schedule -> {
                if (schedule == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                return Response.ok(schedule).build();
            });
    }

    // ============ Billing Processing Endpoints ============

    @POST
    @Path("/process")
    @Operation(summary = "Process due billings")
    public CompletionStage<Response> processDueBillings() {
        BatchBillingCommand command = new BatchBillingCommand(
            null,
            Instant.now()
        );
        return billingService.processDueBillings(command)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @POST
    @Path("/schedules/{id}/process")
    @Operation(summary = "Process a single billing cycle")
    public CompletionStage<Response> processBillingCycle(@PathParam("id") UUID id) {
        BillingScheduleId scheduleId = BillingScheduleId.of(id);
        ProcessBillingCycleCommand command = new ProcessBillingCycleCommand(scheduleId);
        return billingService.processBillingCycle(command)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // ============ Dunning Endpoints ============

    @POST
    @Path("/dunning")
    @Operation(summary = "Process dunning")
    public CompletionStage<Response> processDunning(@Valid ProcessDunningRequest request) {
        ProcessDunningCommand command = new ProcessDunningCommand(
            request.getDaysOverdue(),
            request.getAction()
        );
        return billingService.processDunning(command)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // ============ Query Endpoints ============

    @GET
    @Path("/history/{customerId}")
    @Operation(summary = "Get billing history")
    public CompletionStage<Response> getBillingHistory(@PathParam("customerId") String customerId) {
        return billingService.getBillingHistory(customerId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @GET
    @Path("/upcoming")
    @Operation(summary = "Get upcoming billings")
    public CompletionStage<Response> getUpcomingBillings(
            @QueryParam("daysAhead") @DefaultValue("7") int daysAhead) {
        UpcomingBillingsQuery query = new UpcomingBillingsQuery(daysAhead);
        return billingService.getUpcomingBillings(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @GET
    @Path("/statistics")
    @Operation(summary = "Get billing statistics")
    public CompletionStage<Response> getBillingStatistics(
            @QueryParam("fromDate") String fromDate,
            @QueryParam("toDate") String toDate) {
        BillingStatisticsQuery query = new BillingStatisticsQuery(
            fromDate != null ? Instant.parse(fromDate) : Instant.now().minusSeconds(30L * 24L * 60L * 60L),
            toDate != null ? Instant.parse(toDate) : Instant.now()
        );
        return billingService.getBillingStatistics(query)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // ============ Request/Response DTOs ============

    public static class CreateBillingScheduleRequest {
        private UUID subscriptionId;
        private String customerId;
        private String customerEmail;
        private BillingFrequency frequency;
        private BigDecimal amount;
        private String currencyCode;
        private Instant startDate;
        private int totalCycles;
        private String paymentMethodToken;

        // Getters and setters
        public UUID getSubscriptionId() { return subscriptionId; }
        public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        public BillingFrequency getFrequency() { return frequency; }
        public void setFrequency(BillingFrequency frequency) { this.frequency = frequency; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public Instant getStartDate() { return startDate; }
        public void setStartDate(Instant startDate) { this.startDate = startDate; }
        public int getTotalCycles() { return totalCycles; }
        public void setTotalCycles(int totalCycles) { this.totalCycles = totalCycles; }
        public String getPaymentMethodToken() { return paymentMethodToken; }
        public void setPaymentMethodToken(String paymentMethodToken) { this.paymentMethodToken = paymentMethodToken; }
    }

    public static class CreateBillingScheduleResponse {
        private final BillingScheduleId scheduleId;

        public CreateBillingScheduleResponse(BillingScheduleId scheduleId) {
            this.scheduleId = scheduleId;
        }

        public UUID getScheduleId() {
            return scheduleId.getValue();
        }
    }

    public static class CancelBillingScheduleRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ProcessDunningRequest {
        private int daysOverdue;
        private DunningAction action;

        public int getDaysOverdue() { return daysOverdue; }
        public void setDaysOverdue(int daysOverdue) { this.daysOverdue = daysOverdue; }
        public DunningAction getAction() { return action; }
        public void setAction(DunningAction action) { this.action = action; }
    }
}