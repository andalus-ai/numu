package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.infrastructure.persistence.entity.OpportunityEntity;
import tech.kayys.numou.identity.domain.user.UserId;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Collectors;

/**
 * Mapper between Opportunity domain and persistence entities.
 *
 * Reconstruction goes through the {@code create} factory plus the
 * aggregate's own mutators (including {@code moveStage}, which restores
 * stage-derived probability and weighted value) so the domain never
 * needs persistence-only setters.
 */
@ApplicationScoped
public class OpportunityMapper {

    public OpportunityEntity toEntity(Opportunity opportunity) {
        OpportunityEntity entity = new OpportunityEntity();
        entity.id = opportunity.getId().getValue();
        entity.name = opportunity.getName();
        entity.description = opportunity.getDescription();
        entity.customerId = opportunity.getCustomerId() != null
                ? opportunity.getCustomerId().getValue() : null;
        entity.customerName = opportunity.getCustomerName();
        entity.stage = opportunity.getStage();
        entity.estimatedValue = opportunity.getEstimatedValue();
        entity.probability = opportunity.getProbability();
        entity.weightedValue = opportunity.getWeightedValue();
        entity.currencyCode = opportunity.getCurrencyCode();
        entity.assignedTo = opportunity.getAssignedTo() != null
                ? opportunity.getAssignedTo().value() : null;
        entity.expectedCloseDate = opportunity.getExpectedCloseDate();
        entity.leadSource = opportunity.getLeadSource();
        entity.productInterest = opportunity.getProductInterest();
        entity.competitors = opportunity.getCompetitors();
        entity.decisionCriteria = opportunity.getDecisionCriteria();
        entity.nextStep = opportunity.getNextStep();
        entity.notes = opportunity.getNotes();
        entity.active = opportunity.isActive();
        entity.createdAt = opportunity.getCreatedAt();
        entity.updatedAt = opportunity.getUpdatedAt();
        entity.version = (long) opportunity.getVersion();

        if (opportunity.getActivities() != null) {
            entity.activities = opportunity.getActivities().stream()
                .map(activity -> {
                    OpportunityEntity.OpportunityActivityEntity a = new OpportunityEntity.OpportunityActivityEntity();
                    a.activityType = activity.getActivityType();
                    a.description = activity.getDescription();
                    a.performedBy = activity.getPerformedBy();
                    a.outcome = activity.getOutcome();
                    a.activityDate = activity.getActivityDate();
                    return a;
                })
                .collect(Collectors.toList());
        }

        return entity;
    }

    public Opportunity toDomain(OpportunityEntity entity) {
        Opportunity opportunity = Opportunity.create(
                OpportunityId.of(entity.id),
                entity.name,
                entity.customerId != null ? CustomerId.of(entity.customerId) : null,
                entity.customerName,
                entity.estimatedValue,
                entity.currencyCode);
        opportunity.setDescription(entity.description);
        if (entity.stage != null) {
            opportunity.moveStage(entity.stage);
        }
        if (entity.assignedTo != null) {
            opportunity.assign(UserId.of(entity.assignedTo));
        }
        if (entity.expectedCloseDate != null) {
            opportunity.setExpectedCloseDate(entity.expectedCloseDate);
        }
        opportunity.setLeadSource(entity.leadSource);
        opportunity.setProductInterest(entity.productInterest);
        opportunity.updateCompetitors(entity.competitors, entity.decisionCriteria);
        opportunity.setNextStep(entity.nextStep);
        opportunity.setNotes(entity.notes);
        opportunity.setCreatedAt(entity.createdAt);
        opportunity.setUpdatedAt(entity.updatedAt);
        opportunity.setVersion(entity.version != null ? entity.version.intValue() : 0);
        return opportunity;
    }
}
