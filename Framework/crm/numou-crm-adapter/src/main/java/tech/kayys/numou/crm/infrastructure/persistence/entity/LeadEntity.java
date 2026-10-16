package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lead entity for persistence.
 */
@Entity
@Table(name = "crm_leads", indexes = {
    @Index(name = "idx_lead_email", columnList = "email"),
    @Index(name = "idx_lead_status", columnList = "status"),
    @Index(name = "idx_lead_assigned", columnList = "assigned_to")
})
public class LeadEntity extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 50)
    public String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    public String lastName;

    @Column(name = "email", length = 100)
    public String email;

    @Column(name = "phone", length = 20)
    public String phone;

    @Column(name = "company", length = 100)
    public String company;

    @Column(name = "job_title", length = 100)
    public String jobTitle;

    @Column(name = "industry", length = 50)
    public String industry;

    @Column(name = "source", length = 50)
    public String source;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    public LeadStatus status;

    @Column(name = "assigned_to", columnDefinition = "UUID")
    public UUID assignedTo;

    @Column(name = "notes", length = 2000)
    public String notes;

    @Column(name = "score")
    public double score;

    @ElementCollection
    @CollectionTable(name = "crm_lead_activities", joinColumns = @JoinColumn(name = "lead_id"))
    @AttributeOverrides({
        @AttributeOverride(name = "activityType", column = @Column(name = "activity_type", length = 50)),
        @AttributeOverride(name = "description", column = @Column(name = "description", length = 500)),
        @AttributeOverride(name = "performedBy", column = @Column(name = "performed_by", length = 100)),
        @AttributeOverride(name = "outcome", column = @Column(name = "outcome", length = 200))
    })
    public List<LeadActivityEntity> activities = new ArrayList<>();

    @Embeddable
    public static class LeadActivityEntity {
        public String activityType;
        public String description;
        public String performedBy;
        public String outcome;
        public Instant activityDate;
    }
}