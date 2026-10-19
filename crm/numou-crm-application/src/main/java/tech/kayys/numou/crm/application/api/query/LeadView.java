package tech.kayys.numou.crm.application.api.query;

import tech.kayys.numou.crm.domain.model.Lead;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * View of a lead.
 */
public record LeadView(
        String leadId,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String company,
        String jobTitle,
        String industry,
        String source,
        String status,
        String assignedTo,
        double score,
        List<LeadActivityView> activities,
        Instant createdAt,
        Instant updatedAt
) {

    public static LeadView fromDomain(Lead lead) {
        return new LeadView(
            lead.getId().toString(),
            lead.getFirstName(),
            lead.getLastName(),
            lead.getFullName(),
            lead.getEmail(),
            lead.getPhone(),
            lead.getCompany(),
            lead.getJobTitle(),
            lead.getIndustry(),
            lead.getSource(),
            lead.getStatus().name(),
            lead.getAssignedTo() != null ? lead.getAssignedTo().value().toString() : null,
            lead.getScore(),
            lead.getActivities().stream()
                .map(LeadActivityView::fromDomain)
                .collect(Collectors.toList()),
            lead.getCreatedAt(),
            lead.getUpdatedAt()
        );
    }

    public record LeadActivityView(
            String activityType,
            String description,
            String performedBy,
            String outcome,
            Instant activityDate
    ) {
        public static LeadActivityView fromDomain(Lead.LeadActivity activity) {
            return new LeadActivityView(
                activity.getActivityType(),
                activity.getDescription(),
                activity.getPerformedBy(),
                activity.getOutcome(),
                activity.getActivityDate()
            );
        }
    }
}