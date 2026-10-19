package tech.kayys.numou.analytics.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.analytics.domain.identifier.KPIId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KPI definition aggregate root.
 * Defines a Key Performance Indicator and its targets.
 */
public final class KPIDefinition extends AbstractAggregateRoot<KPIId> {
    
    private static final long serialVersionUID = 1L;
    
    private String code;
    private String name;
    private String description;
    private String category;
    private String formula;
    private String unit;
    private double targetValue;
    private double minValue;
    private double maxValue;
    private String direction; // UP, DOWN, NEUTRAL
    private String frequency;
    private List<String> dataSources;
    private String owner;
    private boolean active;
    private String notes;

    private KPIDefinition(KPIId id) {
        super(id);
        this.dataSources = new ArrayList<>();
        this.active = true;
        this.direction = "UP";
    }

    private KPIDefinition() {
        super();
    }

    /**
     * Factory method to create a new KPI definition.
     */
    public static KPIDefinition create(
            KPIId id,
            String code,
            String name,
            String category,
            String formula,
            String unit,
            double targetValue,
            String owner) {
        KPIDefinition kpi = new KPIDefinition(id);
        kpi.code = code;
        kpi.name = name;
        kpi.category = category;
        kpi.formula = formula;
        kpi.unit = unit;
        kpi.targetValue = targetValue;
        kpi.owner = owner;
        return kpi;
    }

    /**
     * Adds a data source.
     */
    public void addDataSource(String dataSource) {
        if (!dataSources.contains(dataSource)) {
            dataSources.add(dataSource);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a data source.
     */
    public void removeDataSource(String dataSource) {
        dataSources.remove(dataSource);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the min and max values.
     */
    public void setRange(double min, double max) {
        this.minValue = min;
        this.maxValue = max;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the target.
     */
    public void updateTarget(double targetValue) {
        this.targetValue = targetValue;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the KPI information.
     */
    public void update(String name, String description, String formula) {
        this.name = name;
        this.description = description;
        this.formula = formula;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Activates the KPI.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the KPI.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the direction.
     */
    public void setDirection(String direction) {
        this.direction = direction;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the frequency.
     */
    public void setFrequency(String frequency) {
        this.frequency = frequency;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getFormula() { return formula; }
    public String getUnit() { return unit; }
    public double getTargetValue() { return targetValue; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
    public String getDirection() { return direction; }
    public String getFrequency() { return frequency; }
    public List<String> getDataSources() { return Collections.unmodifiableList(dataSources); }
    public String getOwner() { return owner; }
    public boolean isActive() { return active; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
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
        return "KPIDefinition{" +
                "id=" + getId() +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", targetValue=" + targetValue +
                ", active=" + active +
                '}';
    }
}