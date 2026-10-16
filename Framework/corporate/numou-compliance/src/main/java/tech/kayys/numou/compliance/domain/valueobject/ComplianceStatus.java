package tech.kayys.syirkah.compliance.domain.valueobject;

/**
 * Status of a compliance requirement.
 */
public enum ComplianceStatus {
    PENDING("Pending - awaiting assessment"),
    IN_PROGRESS("In Progress - being addressed"),
    COMPLIANT("Compliant - requirements met"),
    NON_COMPLIANT("Non-Compliant - requirements not met"),
    EXEMPTED("Exempted - not applicable"),
    UNDER_REVIEW("Under Review - being evaluated"),
    PARTIALLY_COMPLIANT("Partially Compliant - some requirements met");

    private final String description;

    ComplianceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompliant() {
        return this == COMPLIANT || this == EXEMPTED;
    }
}
