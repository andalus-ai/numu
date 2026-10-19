package tech.kayys.numou.analytics.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.analytics.domain.identifier.ReportId;
import tech.kayys.numou.analytics.domain.valueobject.ReportFrequency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report definition aggregate root.
 * Defines a report configuration and schedule.
 */
public final class ReportDefinition extends AbstractAggregateRoot<ReportId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String category;
    private String dataSource;
    private String query;
    private String dimensions;
    private String metrics;
    private String filters;
    private String sorting;
    private String outputFormat;
    private ReportFrequency frequency;
    private List<String> recipients;
    private String owner;
    private boolean active;
    private boolean scheduled;
    private String scheduleCron;
    private String lastRunAt;
    private String lastRunStatus;
    private String notes;

    private ReportDefinition(ReportId id) {
        super(id);
        this.recipients = new ArrayList<>();
        this.active = true;
        this.scheduled = false;
        this.outputFormat = "PDF";
        this.frequency = ReportFrequency.ON_DEMAND;
    }

    private ReportDefinition() {
        super();
    }

    /**
     * Factory method to create a new report definition.
     */
    public static ReportDefinition create(
            ReportId id,
            String name,
            String description,
            String category,
            String dataSource,
            String query,
            String owner) {
        ReportDefinition report = new ReportDefinition(id);
        report.name = name;
        report.description = description;
        report.category = category;
        report.dataSource = dataSource;
        report.query = query;
        report.owner = owner;
        return report;
    }

    /**
     * Sets the report dimensions and metrics.
     */
    public void setDimensionsAndMetrics(String dimensions, String metrics) {
        this.dimensions = dimensions;
        this.metrics = metrics;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a filter to the report.
     */
    public void addFilter(String filter) {
        if (filters == null || filters.isEmpty()) {
            this.filters = filter;
        } else {
            this.filters = this.filters + "|" + filter;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the sorting.
     */
    public void setSorting(String sorting) {
        this.sorting = sorting;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Schedules the report.
     */
    public void schedule(ReportFrequency frequency, String scheduleCron) {
        this.frequency = frequency;
        this.scheduleCron = scheduleCron;
        this.scheduled = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Unschedules the report.
     */
    public void unschedule() {
        this.scheduled = false;
        this.scheduleCron = null;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a recipient to the report.
     */
    public void addRecipient(String recipient) {
        if (!recipients.contains(recipient)) {
            recipients.add(recipient);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a recipient from the report.
     */
    public void removeRecipient(String recipient) {
        recipients.remove(recipient);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the report.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the report.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Records the last run.
     */
    public void recordLastRun(String status) {
        this.lastRunAt = Instant.now().toString();
        this.lastRunStatus = status;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the number of recipients.
     */
    public int getRecipientCount() {
        return recipients.size();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getDataSource() { return dataSource; }
    public String getQuery() { return query; }
    public String getDimensions() { return dimensions; }
    public String getMetrics() { return metrics; }
    public String getFilters() { return filters; }
    public String getSorting() { return sorting; }
    public String getOutputFormat() { return outputFormat; }
    public ReportFrequency getFrequency() { return frequency; }
    public List<String> getRecipients() { return Collections.unmodifiableList(recipients); }
    public String getOwner() { return owner; }
    public boolean isActive() { return active; }
    public boolean isScheduled() { return scheduled; }
    public String getScheduleCron() { return scheduleCron; }
    public String getLastRunAt() { return lastRunAt; }
    public String getLastRunStatus() { return lastRunStatus; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCategory(String category) {
        this.category = category;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
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
        return "ReportDefinition{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", frequency=" + frequency +
                ", scheduled=" + scheduled +
                ", active=" + active +
                '}';
    }
}