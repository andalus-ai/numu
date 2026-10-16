package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.model.Opportunity;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete view of an opportunity.
 */
public record OpportunityView(
        String opportunityId,
        String name,
        String description,
        String customerId,
        String customerName,
        String stage,
        String stageDescription,
        double estimatedValue,
        double probability,
        double weightedValue,
        String currencyCode,
        String assignedTo,
        String expectedCloseDate,
        String leadSource,
        String productInterest,
        String competitors,
        String decisionCriteria,
        String nextStep,
        List<ActivityView> activities,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        boolean active,
        boolean atRisk,
        boolean stale,
        long ageDays
) {

    public static OpportunityView fromDomain(Opportunity opportunity) {
        return new OpportunityView(
            opportunity.getId().toString(),
            opportunity.getName(),
            opportunity.getDescription(),
            opportunity.getCustomerId().toString(),
            opportunity.getCustomerName(),
            opportunity.getStage().name(),
            opportunity.getStage().getDescription(),
            opportunity.getEstimatedValue(),
            opportunity.getProbability(),
            opportunity.getWeightedValue(),
            opportunity.getCurrencyCode(),
            opportunity.getAssignedTo() != null
                    ? opportunity.getAssignedTo().value().toString() : null,
            opportunity.getExpectedCloseDate() != null ? 
                opportunity.getExpectedCloseDate().toString() : null,
            opportunity.getLeadSource(),
            opportunity.getProductInterest(),
            opportunity.getCompetitors(),
            opportunity.getDecisionCriteria(),
            opportunity.getNextStep(),
            opportunity.getActivities().stream()
                .map(ActivityView::fromDomain)
                .collect(Collectors.toList()),
            opportunity.getNotes(),
            opportunity.getCreatedAt(),
            opportunity.getUpdatedAt(),
            opportunity.isActive(),
            opportunity.isAtRisk(),
            opportunity.isStale(),
            opportunity.getAgeDays()
        );
    }

    public record ActivityView(
            String activityType,
            String description,
            String performedBy,
            String outcome,
            Instant activityDate
    ) {
        public static ActivityView fromDomain(Opportunity.OpportunityActivity activity) {
            return new ActivityView(
                activity.getActivityType(),
                activity.getDescription(),
                activity.getPerformedBy(),
                activity.getOutcome(),
                activity.getActivityDate()
            );
        }
    }
}