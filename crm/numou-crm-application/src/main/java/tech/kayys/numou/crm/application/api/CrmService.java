package tech.kayys.numou.crm.application.api;

import tech.kayys.numou.crm.application.api.command.*;
import tech.kayys.numou.crm.application.api.query.*;
import tech.kayys.numou.crm.domain.identifier.*;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface CrmService {

    // Leads
    CompletionStage<LeadId> createLead(CreateLeadCommand command);
    CompletionStage<LeadView> getLead(GetLeadQuery query);
    CompletionStage<CustomerId> convertLead(ConvertLeadCommand command);
    CompletionStage<List<LeadView>> searchLeads(SearchLeadsQuery query);

    // Customers
    CompletionStage<CustomerId> createCustomer(CreateCustomerCommand command);
    CompletionStage<CustomerView> getCustomer(GetCustomerQuery query);
    CompletionStage<Void> updateCustomer(UpdateCustomerCommand command);
    CompletionStage<Void> addCustomerContact(AddCustomerContactCommand command);
    CompletionStage<List<CustomerView>> searchCustomers(SearchCustomersQuery query);

    // Opportunities
    CompletionStage<OpportunityId> createOpportunity(CreateOpportunityCommand command);
    CompletionStage<OpportunityView> getOpportunity(GetOpportunityQuery query);
    CompletionStage<Void> updateOpportunity(UpdateOpportunityCommand command);
    CompletionStage<Void> moveOpportunityStage(MoveOpportunityStageCommand command);
    CompletionStage<PipelineView> getPipeline(String assignedTo, UUID customerId);
    CompletionStage<List<OpportunityView>> searchOpportunities(SearchOpportunitiesQuery query);

    // Referral
    CompletionStage<ReferralId> createReferral(CreateReferralCommand command);
    CompletionStage<Void> acceptReferral(AcceptReferralCommand command);
    CompletionStage<Void> rejectReferral(RejectReferralCommand command);
    CompletionStage<Void> convertReferral(ConvertReferralCommand command);
    CompletionStage<Void> cancelReferral(CancelReferralCommand command);

    // Territory
    CompletionStage<TerritoryId> createTerritory(CreateTerritoryCommand command);
    CompletionStage<Void> updateTerritory(UpdateTerritoryCommand command);
    CompletionStage<Void> activateTerritory(ActivateTerritoryCommand command);
    CompletionStage<Void> retireTerritory(RetireTerritoryCommand command);
    CompletionStage<Void> setTerritoryParent(SetTerritoryParentCommand command);

    // Territory Assignment
    CompletionStage<TerritoryAssignmentId> createTerritoryAssignment(CreateTerritoryAssignmentCommand command);
    CompletionStage<Void> endTerritoryAssignment(EndTerritoryAssignmentCommand command);

    // Reports / Dashboard
    CompletionStage<CrmDashboardView> getDashboardMetrics(String period);
    CompletionStage<ReportId> generateConversionReport(GenerateConversionReportCommand command);
    CompletionStage<ConversionReportView> getConversionReport(String reportId);
    CompletionStage<ConversionReportView> getLatestConversionReport();
    CompletionStage<Object> getPipelineAnalytics(String assignedTo, UUID customerId);
    CompletionStage<Object> getLeadSourceAnalytics(String period, Instant fromDate, Instant toDate);

    // Email
    CompletionStage<EmailMessageId> sendEmail(SendEmailCommand command);
    CompletionStage<EmailTemplateId> createEmailTemplate(CreateEmailTemplateCommand command);
    CompletionStage<CampaignId> createEmailCampaign(CreateEmailCampaignCommand command);
    CompletionStage<Void> startEmailCampaign(StartEmailCampaignCommand command);

}
