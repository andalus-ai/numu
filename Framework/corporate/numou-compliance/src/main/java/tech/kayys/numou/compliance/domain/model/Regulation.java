package tech.kayys.syirkah.compliance.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.compliance.domain.identifier.RegulationId;
import tech.kayys.syirkah.compliance.domain.valueobject.RegulationType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Regulation aggregate root.
 * Represents a regulatory framework that imposes compliance requirements.
 */
public final class Regulation extends AbstractAggregateRoot<RegulationId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private RegulationType regulationType;
    private String jurisdiction;
    private String effectiveDate;
    private String expirationDate;
    private List<String> applicableIndustries;
    private List<String> applicableRegions;
    private String requirementsSummary;
    private String penalties;
    private List<String> documentationUrls;
    private String notes;
    private boolean active;

    private Regulation(RegulationId id) {
        super(id);
        this.applicableIndustries = new ArrayList<>();
        this.applicableRegions = new ArrayList<>();
        this.documentationUrls = new ArrayList<>();
        this.active = true;
    }

    private Regulation() {
        super();
    }

    /**
     * Factory method to create a new regulation.
     */
    public static Regulation create(
            RegulationId id,
            String name,
            RegulationType regulationType,
            String jurisdiction) {
        Regulation regulation = new Regulation(id);
        regulation.name = name;
        regulation.regulationType = regulationType;
        regulation.jurisdiction = jurisdiction;
        return regulation;
    }

    /**
     * Adds an applicable industry.
     */
    public void addApplicableIndustry(String industry) {
        if (!applicableIndustries.contains(industry)) {
            applicableIndustries.add(industry);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Adds an applicable region.
     */
    public void addApplicableRegion(String region) {
        if (!applicableRegions.contains(region)) {
            applicableRegions.add(region);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Adds a documentation URL.
     */
    public void addDocumentationUrl(String url) {
        if (!documentationUrls.contains(url)) {
            documentationUrls.add(url);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Activates the regulation.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the regulation.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public RegulationType getRegulationType() { return regulationType; }
    public String getJurisdiction() { return jurisdiction; }
    public String getEffectiveDate() { return effectiveDate; }
    public String getExpirationDate() { return expirationDate; }
    public List<String> getApplicableIndustries() { return Collections.unmodifiableList(applicableIndustries); }
    public List<String> getApplicableRegions() { return Collections.unmodifiableList(applicableRegions); }
    public String getRequirementsSummary() { return requirementsSummary; }
    public String getPenalties() { return penalties; }
    public List<String> getDocumentationUrls() { return Collections.unmodifiableList(documentationUrls); }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setRequirementsSummary(String requirementsSummary) {
        this.requirementsSummary = requirementsSummary;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPenalties(String penalties) {
        this.penalties = penalties;
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
        return "Regulation{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", regulationType=" + regulationType +
                ", jurisdiction='" + jurisdiction + '\'' +
                '}';
    }
}
