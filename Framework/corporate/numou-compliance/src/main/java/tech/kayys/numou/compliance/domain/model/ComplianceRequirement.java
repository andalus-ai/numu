package tech.kayys.syirkah.compliance.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.compliance.domain.identifier.ComplianceRequirementId;
import tech.kayys.syirkah.compliance.domain.identifier.RegulationId;
import tech.kayys.syirkah.compliance.domain.valueobject.ComplianceStatus;
import tech.kayys.syirkah.compliance.domain.valueobject.InternalControlType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compliance requirement aggregate root.
 * Represents a specific compliance requirement that must be met.
 */
public final class ComplianceRequirement extends AbstractAggregateRoot<ComplianceRequirementId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private RegulationId regulationId;
    private String regulationName;
    private String requirementCode;
    private ComplianceStatus status;
    private String category;
    private String subCategory;
    private String responsibleParty;
    private List<InternalControlType> controls;
    private String implementationGuidance;
    private String evidenceRequirements;
    private String frequency; // Yearly, Quarterly, Monthly, etc.
    private Instant dueDate;
    private Instant completedDate;
    private String assessedBy;
    private Instant assessedAt;
    private String validatedBy;
    private Instant validatedAt;
    private String notes;
    private boolean active;

    private ComplianceRequirement(ComplianceRequirementId id) {
        super(id);
        this.controls = new ArrayList<>();
        this.status = ComplianceStatus.PENDING;
        this.active = true;
    }

    private ComplianceRequirement() {
        super();
    }

    /**
     * Factory method to create a new compliance requirement.
     */
    public static ComplianceRequirement create(
            ComplianceRequirementId id,
            String name,
            RegulationId regulationId,
            String regulationName,
            String requirementCode,
            String category) {
        ComplianceRequirement requirement = new ComplianceRequirement(id);
        requirement.name = name;
        requirement.regulationId = regulationId;
        requirement.regulationName = regulationName;
        requirement.requirementCode = requirementCode;
        requirement.category = category;
        return requirement;
    }

    /**
     * Adds a control to the requirement.
     */
    public void addControl(InternalControlType control) {
        if (!controls.contains(control)) {
            controls.add(control);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Removes a control from the requirement.
     */
    public void removeControl(InternalControlType control) {
        controls.remove(control);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Updates the status of the requirement.
     */
    public void updateStatus(ComplianceStatus newStatus, String assessedBy) {
        this.status = newStatus;
        this.assessedBy = assessedBy;
        this.assessedAt = Instant.now();
        if (newStatus.isCompliant()) {
            this.completedDate = Instant.now();
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Validates the requirement.
     */
    public void validate(String validatedBy) {
        if (status != ComplianceStatus.COMPLIANT && status != ComplianceStatus.PARTIALLY_COMPLIANT) {
            throw new IllegalStateException("Only compliant requirements can be validated");
        }
        this.validatedBy = validatedBy;
        this.validatedAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Exempts the requirement.
     */
    public void exempt(String reason) {
        this.status = ComplianceStatus.EXEMPTED;
        this.notes = reason;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the due date.
     */
    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Checks if the requirement is overdue.
     */
    public boolean isOverdue() {
        if (status.isCompliant() || status == ComplianceStatus.EXEMPTED) {
            return false;
        }
        return dueDate != null && Instant.now().isAfter(dueDate);
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public RegulationId getRegulationId() { return regulationId; }
    public String getRegulationName() { return regulationName; }
    public String getRequirementCode() { return requirementCode; }
    public ComplianceStatus getStatus() { return status; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public String getResponsibleParty() { return responsibleParty; }
    public List<InternalControlType> getControls() { return Collections.unmodifiableList(controls); }
    public String getImplementationGuidance() { return implementationGuidance; }
    public String getEvidenceRequirements() { return evidenceRequirements; }
    public String getFrequency() { return frequency; }
    public Instant getDueDate() { return dueDate; }
    public Instant getCompletedDate() { return completedDate; }
    public String getAssessedBy() { return assessedBy; }
    public Instant getAssessedAt() { return assessedAt; }
    public String getValidatedBy() { return validatedBy; }
    public Instant getValidatedAt() { return validatedAt; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setResponsibleParty(String responsibleParty) {
        this.responsibleParty = responsibleParty;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setImplementationGuidance(String implementationGuidance) {
        this.implementationGuidance = implementationGuidance;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setEvidenceRequirements(String evidenceRequirements) {
        this.evidenceRequirements = evidenceRequirements;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "ComplianceRequirement{" +
                "id=" + getId() +
                ", requirementCode='" + requirementCode + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", regulationName='" + regulationName + '\'' +
                '}';
    }
}
