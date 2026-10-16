package tech.kayys.numou.crm.application.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.*;
import tech.kayys.numou.crm.application.api.query.*;
import tech.kayys.numou.crm.application.customer.*;
import tech.kayys.numou.crm.application.lead.*;
import tech.kayys.numou.crm.application.opportunity.*;
import tech.kayys.numou.crm.application.referral.*;
import tech.kayys.numou.crm.application.territory.*;
import tech.kayys.numou.crm.application.territoryassignment.*;
import tech.kayys.numou.crm.domain.identifier.*;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.application.result.Result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Composition facade over the CRM use-case handlers.
 *
 * Plain application-layer class (no CDI annotations) so the layer stays
 * framework-free; the adapter's {@code CrmServiceProducer} wires it and
 * exposes it as the {@link CrmService} bean the REST resources inject.
 *
 * Handlers return {@code Uni<Result<T>>} (referral/territory) or
 * {@code Uni<T>} (lead/customer/opportunity); this facade bridges both
 * to the {@code CompletionStage} contract, surfacing failures as an
 * exceptional stage.
 */
public final class CrmServiceImpl implements CrmService {

    private final CreateLeadHandler createLeadHandler;
    private final GetLeadQueryHandler getLeadQueryHandler;
    private final ConvertLeadHandler convertLeadHandler;
    private final SearchLeadsQueryHandler searchLeadsQueryHandler;

    private final CreateCustomerHandler createCustomerHandler;
    private final GetCustomerQueryHandler getCustomerQueryHandler;
    private final UpdateCustomerHandler updateCustomerHandler;
    private final AddCustomerContactHandler addCustomerContactHandler;
    private final SearchCustomersQueryHandler searchCustomersQueryHandler;

    private final CreateOpportunityHandler createOpportunityHandler;
    private final GetOpportunityQueryHandler getOpportunityQueryHandler;
    private final UpdateOpportunityHandler updateOpportunityHandler;
    private final MoveOpportunityStageHandler moveOpportunityStageHandler;
    private final SearchOpportunitiesQueryHandler searchOpportunitiesQueryHandler;
    private final GetPipelineQueryHandler getPipelineQueryHandler;

    private final CreateReferralHandler createReferralHandler;
    private final AcceptReferralHandler acceptReferralHandler;
    private final RejectReferralHandler rejectReferralHandler;
    private final ConvertReferralHandler convertReferralHandler;
    private final CancelReferralHandler cancelReferralHandler;

    private final CreateTerritoryHandler createTerritoryHandler;
    private final UpdateTerritoryHandler updateTerritoryHandler;
    private final ActivateTerritoryHandler activateTerritoryHandler;
    private final RetireTerritoryHandler retireTerritoryHandler;
    private final SetTerritoryParentHandler setTerritoryParentHandler;

    private final CreateTerritoryAssignmentHandler createTerritoryAssignmentHandler;
    private final EndTerritoryAssignmentHandler endTerritoryAssignmentHandler;

    public CrmServiceImpl(
            CreateLeadHandler createLeadHandler,
            GetLeadQueryHandler getLeadQueryHandler,
            ConvertLeadHandler convertLeadHandler,
            SearchLeadsQueryHandler searchLeadsQueryHandler,
            CreateCustomerHandler createCustomerHandler,
            GetCustomerQueryHandler getCustomerQueryHandler,
            UpdateCustomerHandler updateCustomerHandler,
            AddCustomerContactHandler addCustomerContactHandler,
            SearchCustomersQueryHandler searchCustomersQueryHandler,
            CreateOpportunityHandler createOpportunityHandler,
            GetOpportunityQueryHandler getOpportunityQueryHandler,
            UpdateOpportunityHandler updateOpportunityHandler,
            MoveOpportunityStageHandler moveOpportunityStageHandler,
            SearchOpportunitiesQueryHandler searchOpportunitiesQueryHandler,
            GetPipelineQueryHandler getPipelineQueryHandler,
            CreateReferralHandler createReferralHandler,
            AcceptReferralHandler acceptReferralHandler,
            RejectReferralHandler rejectReferralHandler,
            ConvertReferralHandler convertReferralHandler,
            CancelReferralHandler cancelReferralHandler,
            CreateTerritoryHandler createTerritoryHandler,
            UpdateTerritoryHandler updateTerritoryHandler,
            ActivateTerritoryHandler activateTerritoryHandler,
            RetireTerritoryHandler retireTerritoryHandler,
            SetTerritoryParentHandler setTerritoryParentHandler,
            CreateTerritoryAssignmentHandler createTerritoryAssignmentHandler,
            EndTerritoryAssignmentHandler endTerritoryAssignmentHandler) {
        this.createLeadHandler = createLeadHandler;
        this.getLeadQueryHandler = getLeadQueryHandler;
        this.convertLeadHandler = convertLeadHandler;
        this.searchLeadsQueryHandler = searchLeadsQueryHandler;
        this.createCustomerHandler = createCustomerHandler;
        this.getCustomerQueryHandler = getCustomerQueryHandler;
        this.updateCustomerHandler = updateCustomerHandler;
        this.addCustomerContactHandler = addCustomerContactHandler;
        this.searchCustomersQueryHandler = searchCustomersQueryHandler;
        this.createOpportunityHandler = createOpportunityHandler;
        this.getOpportunityQueryHandler = getOpportunityQueryHandler;
        this.updateOpportunityHandler = updateOpportunityHandler;
        this.moveOpportunityStageHandler = moveOpportunityStageHandler;
        this.searchOpportunitiesQueryHandler = searchOpportunitiesQueryHandler;
        this.getPipelineQueryHandler = getPipelineQueryHandler;
        this.createReferralHandler = createReferralHandler;
        this.acceptReferralHandler = acceptReferralHandler;
        this.rejectReferralHandler = rejectReferralHandler;
        this.convertReferralHandler = convertReferralHandler;
        this.cancelReferralHandler = cancelReferralHandler;
        this.createTerritoryHandler = createTerritoryHandler;
        this.updateTerritoryHandler = updateTerritoryHandler;
        this.activateTerritoryHandler = activateTerritoryHandler;
        this.retireTerritoryHandler = retireTerritoryHandler;
        this.setTerritoryParentHandler = setTerritoryParentHandler;
        this.createTerritoryAssignmentHandler = createTerritoryAssignmentHandler;
        this.endTerritoryAssignmentHandler = endTerritoryAssignmentHandler;
    }

    // ---------------------------------------------------------------- Leads

    @Override
    public CompletionStage<LeadId> createLead(CreateLeadCommand command) {
        return stage(createLeadHandler.handle(command));
    }

    @Override
    public CompletionStage<LeadView> getLead(GetLeadQuery query) {
        return stage(getLeadQueryHandler.handle(query));
    }

    @Override
    public CompletionStage<CustomerId> convertLead(ConvertLeadCommand command) {
        return stage(convertLeadHandler.handle(command));
    }

    @Override
    public CompletionStage<List<LeadView>> searchLeads(SearchLeadsQuery query) {
        return stage(searchLeadsQueryHandler.handle(query));
    }

    // ------------------------------------------------------------ Customers

    @Override
    public CompletionStage<CustomerId> createCustomer(CreateCustomerCommand command) {
        return stage(createCustomerHandler.handle(command));
    }

    @Override
    public CompletionStage<CustomerView> getCustomer(GetCustomerQuery query) {
        return stage(getCustomerQueryHandler.handle(query));
    }

    @Override
    public CompletionStage<Void> updateCustomer(UpdateCustomerCommand command) {
        return stage(updateCustomerHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> addCustomerContact(AddCustomerContactCommand command) {
        return stage(addCustomerContactHandler.handle(command));
    }

    @Override
    public CompletionStage<List<CustomerView>> searchCustomers(SearchCustomersQuery query) {
        return stage(searchCustomersQueryHandler.handle(query));
    }

    // ---------------------------------------------------------- Opportunities

    @Override
    public CompletionStage<OpportunityId> createOpportunity(CreateOpportunityCommand command) {
        return stage(createOpportunityHandler.handle(command));
    }

    @Override
    public CompletionStage<OpportunityView> getOpportunity(GetOpportunityQuery query) {
        return stage(getOpportunityQueryHandler.handle(query));
    }

    @Override
    public CompletionStage<Void> updateOpportunity(UpdateOpportunityCommand command) {
        return stage(updateOpportunityHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> moveOpportunityStage(MoveOpportunityStageCommand command) {
        return stage(moveOpportunityStageHandler.handle(command));
    }

    @Override
    public CompletionStage<PipelineView> getPipeline(String assignedTo, UUID customerId) {
        return stage(getPipelineQueryHandler.handle(new GetPipelineQuery(assignedTo, customerId)));
    }

    @Override
    public CompletionStage<List<OpportunityView>> searchOpportunities(SearchOpportunitiesQuery query) {
        return stage(searchOpportunitiesQueryHandler.handle(query));
    }

    // ------------------------------------------------------------- Referral

    @Override
    public CompletionStage<ReferralId> createReferral(CreateReferralCommand command) {
        return unwrap(createReferralHandler.handle(command), Referral::id);
    }

    @Override
    public CompletionStage<Void> acceptReferral(AcceptReferralCommand command) {
        return unwrapVoid(acceptReferralHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> rejectReferral(RejectReferralCommand command) {
        return unwrapVoid(rejectReferralHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> convertReferral(ConvertReferralCommand command) {
        return unwrapVoid(convertReferralHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> cancelReferral(CancelReferralCommand command) {
        return unwrapVoid(cancelReferralHandler.handle(command));
    }

    // ------------------------------------------------------------ Territory

    @Override
    public CompletionStage<TerritoryId> createTerritory(CreateTerritoryCommand command) {
        return unwrap(createTerritoryHandler.handle(command), Territory::id);
    }

    @Override
    public CompletionStage<Void> updateTerritory(UpdateTerritoryCommand command) {
        return unwrapVoid(updateTerritoryHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> activateTerritory(ActivateTerritoryCommand command) {
        return unwrapVoid(activateTerritoryHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> retireTerritory(RetireTerritoryCommand command) {
        return unwrapVoid(retireTerritoryHandler.handle(command));
    }

    @Override
    public CompletionStage<Void> setTerritoryParent(SetTerritoryParentCommand command) {
        return unwrapVoid(setTerritoryParentHandler.handle(command));
    }

    // ------------------------------------------------- Territory Assignment

    @Override
    public CompletionStage<TerritoryAssignmentId> createTerritoryAssignment(CreateTerritoryAssignmentCommand command) {
        return unwrap(createTerritoryAssignmentHandler.handle(command), TerritoryAssignment::id);
    }

    @Override
    public CompletionStage<Void> endTerritoryAssignment(EndTerritoryAssignmentCommand command) {
        return unwrapVoid(endTerritoryAssignmentHandler.handle(command));
    }

    // ---------------------------------------------------- Reports / Dashboard

    @Override
    public CompletionStage<CrmDashboardView> getDashboardMetrics(String period) {
        return unsupported("CRM dashboard metrics");
    }

    @Override
    public CompletionStage<ReportId> generateConversionReport(GenerateConversionReportCommand command) {
        return unsupported("Conversion report generation");
    }

    @Override
    public CompletionStage<ConversionReportView> getConversionReport(String reportId) {
        return unsupported("Conversion report retrieval");
    }

    @Override
    public CompletionStage<ConversionReportView> getLatestConversionReport() {
        return unsupported("Latest conversion report retrieval");
    }

    @Override
    public CompletionStage<Object> getPipelineAnalytics(String assignedTo, UUID customerId) {
        return unsupported("Pipeline analytics");
    }

    @Override
    public CompletionStage<Object> getLeadSourceAnalytics(String period, Instant fromDate, Instant toDate) {
        return unsupported("Lead source analytics");
    }

    // ---------------------------------------------------------------- Email

    @Override
    public CompletionStage<EmailMessageId> sendEmail(SendEmailCommand command) {
        return unsupported("Transactional email");
    }

    @Override
    public CompletionStage<EmailTemplateId> createEmailTemplate(CreateEmailTemplateCommand command) {
        return unsupported("Email templates");
    }

    @Override
    public CompletionStage<CampaignId> createEmailCampaign(CreateEmailCampaignCommand command) {
        return unsupported("Email campaigns");
    }

    @Override
    public CompletionStage<Void> startEmailCampaign(StartEmailCampaignCommand command) {
        return unsupported("Email campaign dispatch");
    }

    // ------------------------------------------------------------- bridging

    private static <T> CompletionStage<T> stage(Uni<T> uni) {
        return uni.subscribe().asCompletionStage();
    }

    private static <T, R> CompletionStage<R> unwrap(Uni<Result<T>> uni, Function<T, R> mapper) {
        return uni.onItem()
                .transform(result -> mapper.apply(result.orElseThrow()))
                .subscribe()
                .asCompletionStage();
    }

    private static <T> CompletionStage<Void> unwrapVoid(Uni<Result<T>> uni) {
        return uni.onItem()
                .transform(Result::orElseThrow)
                .flatMap(value -> Uni.createFrom().voidItem())
                .subscribe()
                .asCompletionStage();
    }

    private static <T> CompletionStage<T> unsupported(String feature) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                feature + " is not implemented yet: the CRM Email (P13) and Reports/analytics "
                        + "blueprint areas have no domain repository, persistence, or events. "
                        + "See the flagged CRM blueprint gap."));
    }
}
