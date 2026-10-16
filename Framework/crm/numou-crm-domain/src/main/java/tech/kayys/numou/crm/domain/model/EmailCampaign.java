package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.CampaignId;
import tech.kayys.numou.crm.domain.identifier.EmailTemplateId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Email campaign aggregate root.
 * Represents a marketing email campaign.
 */
public final class EmailCampaign extends AbstractAggregateRoot<CampaignId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String subject;
    private EmailTemplateId templateId;
    private List<String> recipientGroups;
    private String status; // DRAFT, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    private Instant scheduledAt;
    private Instant sentAt;
    private Instant completedAt;
    private int totalRecipients;
    private int sentCount;
    private int openedCount;
    private int clickedCount;
    private int bouncedCount;
    private int unsubscribedCount;
    private String createdBy;
    private String notes;
    private boolean active;

    private EmailCampaign(CampaignId id) {
        super(id);
        this.recipientGroups = new ArrayList<>();
        this.status = "DRAFT";
        this.active = true;
    }

    private EmailCampaign() {
        super();
    }

    /**
     * Factory method to create a new email campaign.
     */
    public static EmailCampaign create(
            CampaignId id,
            String name,
            String subject,
            String createdBy) {
        EmailCampaign campaign = new EmailCampaign(id);
        campaign.name = name;
        campaign.subject = subject;
        campaign.createdBy = createdBy;
        return campaign;
    }

    /**
     * Sets the template for the campaign.
     */
    public void setTemplate(EmailTemplateId templateId) {
        this.templateId = templateId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a recipient group.
     */
    public void addRecipientGroup(String group) {
        if (!recipientGroups.contains(group)) {
            recipientGroups.add(group);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Schedules the campaign.
     */
    public void schedule(Instant scheduledAt) {
        if (status != "DRAFT") {
            throw new IllegalStateException("Cannot schedule campaign in status: " + status);
        }
        this.status = "SCHEDULED";
        this.scheduledAt = scheduledAt;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Starts the campaign.
     */
    public void start() {
        if (status != "SCHEDULED" && status != "DRAFT") {
            throw new IllegalStateException("Cannot start campaign in status: " + status);
        }
        this.status = "IN_PROGRESS";
        this.sentAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the campaign as completed.
     */
    public void complete() {
        if (status != "IN_PROGRESS") {
            throw new IllegalStateException("Cannot complete campaign in status: " + status);
        }
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Cancels the campaign.
     */
    public void cancel(String reason) {
        if (status == "COMPLETED") {
            throw new IllegalStateException("Cannot cancel completed campaign");
        }
        this.status = "CANCELLED";
        this.active = false;
        this.notes = reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a send.
     */
    public void recordSend() {
        if (status != "IN_PROGRESS") {
            throw new IllegalStateException("Cannot record send in status: " + status);
        }
        this.sentCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records an open.
     */
    public void recordOpen() {
        this.openedCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a click.
     */
    public void recordClick() {
        this.clickedCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records a bounce.
     */
    public void recordBounce() {
        this.bouncedCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records an unsubscribe.
     */
    public void recordUnsubscribe() {
        this.unsubscribedCount++;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the open rate.
     */
    public double getOpenRate() {
        if (sentCount == 0) {
            return 0.0;
        }
        return (double) openedCount / sentCount * 100.0;
    }

    /**
     * Gets the click rate.
     */
    public double getClickRate() {
        if (openedCount == 0) {
            return 0.0;
        }
        return (double) clickedCount / openedCount * 100.0;
    }

    /**
     * Gets the bounce rate.
     */
    public double getBounceRate() {
        if (totalRecipients == 0) {
            return 0.0;
        }
        return (double) bouncedCount / totalRecipients * 100.0;
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public EmailTemplateId getTemplateId() { return templateId; }
    public List<String> getRecipientGroups() { return Collections.unmodifiableList(recipientGroups); }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getTotalRecipients() { return totalRecipients; }
    public int getSentCount() { return sentCount; }
    public int getOpenedCount() { return openedCount; }
    public int getClickedCount() { return clickedCount; }
    public int getBouncedCount() { return bouncedCount; }
    public int getUnsubscribedCount() { return unsubscribedCount; }
    public String getCreatedBy() { return createdBy; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTotalRecipients(int totalRecipients) {
        this.totalRecipients = totalRecipients;
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
        return "EmailCampaign{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", sent=" + sentCount +
                ", opened=" + openedCount +
                ", clicked=" + clickedCount +
                '}';
    }
}