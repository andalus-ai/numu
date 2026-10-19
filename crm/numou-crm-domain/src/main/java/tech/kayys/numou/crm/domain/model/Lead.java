package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.valueobject.LeadStatus;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lead aggregate root.
 * Represents a potential customer.
 */
public final class Lead extends AbstractAggregateRoot<LeadId> {
    
    private static final long serialVersionUID = 1L;
    
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String company;
    private String jobTitle;
    private String industry;
    private String source;
    private LeadStatus status;
    private UserId assignedTo;
    private String notes;
    private List<LeadActivity> activities;
    private double score;
    private boolean active;

    public Lead(LeadId id) {
        super(id);
        this.status = LeadStatus.NEW;
        this.activities = new ArrayList<>();
        this.active = true;
        this.score = 0.0;
    }

    private Lead() {
        super();
    }

    /**
     * Factory method to create a new lead.
     */
    public static Lead create(
            LeadId id,
            String firstName,
            String lastName,
            String email,
            String source) {
        Lead lead = new Lead(id);
        lead.firstName = firstName;
        lead.lastName = lastName;
        lead.email = email;
        lead.source = source;
        return lead;
    }

    /**
     * Updates lead information.
     */
    public void update(String firstName, String lastName, String phone, String company, String jobTitle) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.company = company;
        this.jobTitle = jobTitle;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Changes the lead status.
     */
    public void changeStatus(LeadStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Cannot transition from " + status + " to " + newStatus);
        }
        this.status = newStatus;
        if (newStatus == LeadStatus.CONVERTED || newStatus == LeadStatus.LOST || newStatus == LeadStatus.ARCHIVED) {
            this.active = false;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Assigns the lead to a salesperson.
     */
    public void assign(UserId assignedTo) {
        this.assignedTo = java.util.Objects.requireNonNull(assignedTo, "assignedTo cannot be null");
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an activity to the lead.
     */
    public void addActivity(LeadActivity activity) {
        activities.add(activity);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the lead score.
     */
    public void updateScore(double newScore) {
        this.score = newScore;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the lead's full name.
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Getters
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCompany() { return company; }
    public String getJobTitle() { return jobTitle; }
    public String getIndustry() { return industry; }
    public String getSource() { return source; }
    public LeadStatus getStatus() { return status; }
    public UserId getAssignedTo() { return assignedTo; }
    public String getNotes() { return notes; }
    public List<LeadActivity> getActivities() { return Collections.unmodifiableList(activities); }
    public double getScore() { return score; }
    public boolean isActive() { return active; }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setSource(String source) { this.source = source; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public void setAssignedTo(UserId assignedTo) { this.assignedTo = assignedTo; }
    public void setScore(double score) { this.score = score; }
    public void setActive(boolean active) { this.active = active; }

    public void setPhone(String phone) {
        this.phone = phone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCompany(String company) {
        this.company = company;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIndustry(String industry) {
        this.industry = industry;
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
        return "Lead{" +
                "id=" + getId() +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", status=" + status +
                ", score=" + score +
                '}';
    }

    /**
     * Lead activity value object.
     */
    public static final class LeadActivity implements ValueObject {
        private static final long serialVersionUID = 1L;
        
        private final String activityType;
        private final String description;
        private final Instant activityDate;
        private final String performedBy;
        private final String outcome;

        public LeadActivity(String activityType, String description, String performedBy, String outcome) {
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
            return "LeadActivity{" +
                    "activityType='" + activityType + '\'' +
                    ", description='" + description + '\'' +
                    ", activityDate=" + activityDate +
                    '}';
        }
    }
}