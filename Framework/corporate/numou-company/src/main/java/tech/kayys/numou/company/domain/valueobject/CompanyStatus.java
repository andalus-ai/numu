package tech.kayys.syirkah.company.domain.valueobject;

/**
 * Status of a company.
 */
public enum CompanyStatus {
    ACTIVE("Active - fully operational"),
    INACTIVE("Inactive - temporarily closed"),
    SUSPENDED("Suspended - under review"),
    CLOSED("Closed - permanently closed");

    private final String description;

    CompanyStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
