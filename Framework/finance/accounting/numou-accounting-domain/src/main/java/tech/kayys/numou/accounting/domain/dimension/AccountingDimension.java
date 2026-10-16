package tech.kayys.syirkah.accounting.domain.dimension;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * Multi-dimensional accounting classification tags (Cost Center, Profit Center, Project, etc.).
 */
public enum AccountingDimension implements ValueObject {
    COST_CENTER("Cost Center"),
    PROFIT_CENTER("Profit Center"),
    DEPARTMENT("Department"),
    PROJECT("Project"),
    BRANCH("Branch / Location"),
    SEGMENT("Business Segment");

    private final String displayName;

    AccountingDimension(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
