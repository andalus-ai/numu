package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.identifier.OpportunityId;
import tech.kayys.numou.crm.domain.valueobject.OpportunityStage;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Opportunity aggregate root.
 * Represents a sales opportunity.
 */
public final class Opportunity extends AbstractAggregateRoot<OpportunityId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private CustomerId customerId;
    private String customerName;
    private OpportunityStage stage;
    private double estimatedValue;
    private double probability;
    private double weightedValue;
    private String currencyCode;
    private UserId assignedTo;
    private Instant expectedCloseDate;
    private String leadSource;
    private String productInterest;
    private String competitors;
    private String decisionCriteria;
    private String nextStep;
    private List<OpportunityActivity> activities;
    private boolean active;
    private String notes;

    private Opportunity(OpportunityId id) {
        super(id);
        this.activities = new ArrayList<>();
        this.active = true;
        this.stage = OpportunityStage.PROSPECTING;
        this.probability = 0.1;
    }

    private Opportunity() {
        super();
    }

    /**
     * Factory method to create a new opportunity.
     */
    public static Opportunity create(
            OpportunityId id,
            String name,
            CustomerId customerId,
            String customerName,
            double estimatedValue,
            String currencyCode) {
        Opportunity opportunity = new Opportunity(id);
        opportunity.name = name;
        opportunity.customerId = customerId;
        opportunity.customerName = customerName;
        opportunity.estimatedValue = estimatedValue;
        opportunity.currencyCode = currencyCode;
        opportunity.probability = 0.1;
        opportunity.weightedValue = estimatedValue * 0.1;
        return opportunity;
    }

    /**
     * Updates the opportunity details.
     */
    public void update(String name, String description, double estimatedValue) {
        this.name = name;
        this.description = description;
        this.estimatedValue = estimatedValue;
        calculateWeightedValue();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Moves the opportunity to a new stage.
     */
    public void moveStage(OpportunityStage newStage) {
        if (!stage.isWinnable() && newStage != OpportunityStage.WON && newStage != OpportunityStage.LOST) {
            throw new IllegalStateException("Cannot move from terminal stage");
        }
        this.stage = newStage;
        this.probability = newStage.getProbability();
        calculateWeightedValue();
        
        if (newStage == OpportunityStage.WON || newStage == OpportunityStage.LOST) {
            this.active = false;
        }
        
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Assigns the opportunity to a salesperson.
     */
    public void assign(UserId assignedTo) {
        this.assignedTo = java.util.Objects.requireNonNull(assignedTo, "assignedTo cannot be null");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the expected close date.
     */
    public void setExpectedCloseDate(Instant expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an activity to the opportunity.
     */
    public void addActivity(OpportunityActivity activity) {
        activities.add(activity);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates competitive information.
     */
    public void updateCompetitors(String competitors, String decisionCriteria) {
        this.competitors = competitors;
        this.decisionCriteria = decisionCriteria;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the next step.
     */
    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private void calculateWeightedValue() {
        this.weightedValue = estimatedValue * probability;
    }

    /**
     * Gets the win probability as a percentage.
     */
    public double getWinProbabilityPercentage() {
        return probability * 100.0;
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CustomerId getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public OpportunityStage getStage() { return stage; }
    public double getEstimatedValue() { return estimatedValue; }
    public double getProbability() { return probability; }
    public double getWeightedValue() { return weightedValue; }
    public String getCurrencyCode() { return currencyCode; }
    public UserId getAssignedTo() { return assignedTo; }
    public Instant getExpectedCloseDate() { return expectedCloseDate; }
    public String getLeadSource() { return leadSource; }
    public String getProductInterest() { return productInterest; }
    public String getCompetitors() { return competitors; }
    public String getDecisionCriteria() { return decisionCriteria; }
    public String getNextStep() { return nextStep; }
    public List<OpportunityActivity> getActivities() { return Collections.unmodifiableList(activities); }
    public boolean isActive() { return active; }
    public boolean isAtRisk() {
        return expectedCloseDate != null && Instant.now().isAfter(expectedCloseDate) && stage != OpportunityStage.WON;
    }
    public boolean isStale() {
        return getAgeDays() > 30;
    }
    public long getAgeDays() {
        return getCreatedAt() != null ? java.time.temporal.ChronoUnit.DAYS.between(getCreatedAt(), Instant.now()) : 0;
    }
    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }
    public String getNotes() { return notes; }

    public void setLeadSource(String leadSource) {
        this.leadSource = leadSource;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setProductInterest(String productInterest) {
        this.productInterest = productInterest;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Opportunity{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", customerName='" + customerName + '\'' +
                ", stage=" + stage +
                ", value=" + estimatedValue +
                ", weighted=" + weightedValue +
                '}';
    }

    /**
     * Opportunity activity value object.
     */
    public static final class OpportunityActivity implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String activityType;
        private final String description;
        private final Instant activityDate;
        private final String performedBy;
        private final String outcome;

        public OpportunityActivity(String activityType, String description, String performedBy, String outcome) {
            this.activityType = activityType;
            this.description = description;
            this.performedBy = performedBy;
            this.outcome = outcome;
            this.activityDate = Instant.now();
            validate();
        }

        @Override
        public void validate() {
            if (activityType == null || activityType.trim().isEmpty()) {
                throw new IllegalArgumentException("Activity type cannot be empty");
            }
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("Description cannot be empty");
            }
        }

        public String getActivityType() { return activityType; }
        public String getDescription() { return description; }
        public Instant getActivityDate() { return activityDate; }
        public String getPerformedBy() { return performedBy; }
        public String getOutcome() { return outcome; }

        @Override
        public String toString() {
            return "OpportunityActivity{" +
                    "activityType='" + activityType + '\'' +
                    ", description='" + description + '\'' +
                    ", activityDate=" + activityDate +
                    '}';
        }
    }
}