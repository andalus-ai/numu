package tech.kayys.numou.crm.application.api.query;

/**
 * CRM dashboard metrics view.
 */
public record CrmDashboardView(
        // Lead Metrics
        int totalLeads,
        int newLeads,
        int qualifiedLeads,
        int convertedLeads,
        double conversionRate,
        
        // Opportunity Metrics
        int totalOpportunities,
        int openOpportunities,
        int wonOpportunities,
        int lostOpportunities,
        double totalPipelineValue,
        double totalWeightedPipelineValue,
        double averageDealSize,
        
        // Ticket Metrics
        int totalTickets,
        int openTickets,
        int resolvedTickets,
        int overdueTickets,
        
        // Customer Metrics
        int totalCustomers,
        int activeCustomers,
        int newCustomersThisPeriod,
        
        // Period Information
        String periodStart,
        String periodEnd,
        String updatedAt
) {}