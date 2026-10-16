package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.infrastructure.persistence.entity.LeadEntity;
import tech.kayys.numou.identity.domain.user.UserId;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Collectors;

/**
 * Mapper between Lead domain and persistence entities.
 */
@ApplicationScoped
public class LeadMapper {

    public LeadEntity toEntity(Lead lead) {
        LeadEntity entity = new LeadEntity();
        entity.id = lead.getId().getValue();
        entity.firstName = lead.getFirstName();
        entity.lastName = lead.getLastName();
        entity.email = lead.getEmail();
        entity.phone = lead.getPhone();
        entity.company = lead.getCompany();
        entity.jobTitle = lead.getJobTitle();
        entity.industry = lead.getIndustry();
        entity.source = lead.getSource();
        entity.status = lead.getStatus();
        entity.assignedTo = lead.getAssignedTo() != null
                ? lead.getAssignedTo().value() : null;
        entity.notes = lead.getNotes();
        entity.score = lead.getScore();
        entity.active = lead.isActive();
        entity.createdAt = lead.getCreatedAt();
        entity.updatedAt = lead.getUpdatedAt();
        entity.version = (long) lead.getVersion();
        
        if (lead.getActivities() != null) {
            entity.activities = lead.getActivities().stream()
                .map(activity -> {
                    LeadEntity.LeadActivityEntity a = new LeadEntity.LeadActivityEntity();
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

    public Lead toDomain(LeadEntity entity) {
        Lead lead = new Lead(LeadId.of(entity.id));
        lead.setFirstName(entity.firstName);
        lead.setLastName(entity.lastName);
        lead.setEmail(entity.email);
        lead.setPhone(entity.phone);
        lead.setCompany(entity.company);
        lead.setJobTitle(entity.jobTitle);
        lead.setIndustry(entity.industry);
        lead.setSource(entity.source);
        lead.setStatus(entity.status);
        lead.setAssignedTo(entity.assignedTo != null ? UserId.of(entity.assignedTo) : null);
        lead.setNotes(entity.notes);
        lead.setScore(entity.score);
        lead.setActive(entity.active);
        lead.setCreatedAt(entity.createdAt);
        lead.setUpdatedAt(entity.updatedAt);
        lead.setVersion(entity.version != null ? entity.version.intValue() : 0);
        // Activities would be set separately
        return lead;
    }
}