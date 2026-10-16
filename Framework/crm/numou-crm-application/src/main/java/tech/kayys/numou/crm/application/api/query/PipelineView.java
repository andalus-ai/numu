package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;

import java.util.List;

/**
 * Pipeline view for sales stage tracking.
 */
public record PipelineView(
        List<PipelineStageView> stages,
        double totalValue,
        double totalWeightedValue,
        int totalOpportunities,
        int wonCount,
        int lostCount,
        int activeCount
) {

    public record PipelineStageView(
            OpportunityStage stage,
            String stageName,
            String stageDescription,
            int opportunityCount,
            double totalValue,
            double totalWeightedValue,
            List<OpportunitySummaryView> opportunities
    ) {}

    public record OpportunitySummaryView(
            String opportunityId,
            String name,
            String customerName,
            double estimatedValue,
            double probability,
            double weightedValue,
            String assignedTo,
            String expectedCloseDate
    ) {}
}