package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.foundation.persistence.BaseEntity;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opportunity entity for persistence.
 */
@Entity
@Table(name = "crm_opportunities", indexes = {
    @Index(name = "idx_opp_customer", columnList = "customer_id"),
    @Index(name = "idx_opp_stage", columnList = "stage"),
    @Index(name = "idx_opp_assigned", columnList = "assigned_to")
})
public class OpportunityEntity extends BaseEntity {

    @Column(name = "tenant_id", columnDefinition = "UUID")
    public UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "customer_id", columnDefinition = "UUID")
    public UUID customerId;

    @Column(name = "customer_name", length = 100)
    public String customerName;

    @Column(name = "stage", nullable = false)
    @Enumerated(EnumType.STRING)
    public OpportunityStage stage;

    @Column(name = "estimated_value")
    public double estimatedValue;

    @Column(name = "probability")
    public double probability;

    @Column(name = "weighted_value")
    public double weightedValue;

    @Column(name = "currency_code", length = 3)
    public String currencyCode;

    @Column(name = "assigned_to")
    public UUID assignedTo;

    @Column(name = "expected_close_date")
    public Instant expectedCloseDate;

    @Column(name = "lead_source", length = 50)
    public String leadSource;

    @Column(name = "product_interest", length = 255)
    public String productInterest;

    @Column(name = "competitors", length = 500)
    public String competitors;

    @Column(name = "decision_criteria", length = 500)
    public String decisionCriteria;

    @Column(name = "next_step", length = 255)
    public String nextStep;

    @Column(name = "notes", length = 2000)
    public String notes;

    @ElementCollection
    @CollectionTable(name = "crm_opportunity_activities", joinColumns = @JoinColumn(name = "opportunity_id"))
    @AttributeOverrides({
        @AttributeOverride(name = "activityType", column = @Column(name = "activity_type", length = 50)),
        @AttributeOverride(name = "description", column = @Column(name = "description", length = 500)),
        @AttributeOverride(name = "performedBy", column = @Column(name = "performed_by", length = 100)),
        @AttributeOverride(name = "outcome", column = @Column(name = "outcome", length = 200))
    })
    public List<OpportunityActivityEntity> activities = new ArrayList<>();

    @Embeddable
    public static class OpportunityActivityEntity {
        public String activityType;
        public String description;
        public String performedBy;
        public String outcome;
        public Instant activityDate;
    }
}