package tech.kayys.syirkah.accounting.domain.cost;

/** Strategy for distributing pooled costs across cost centers. */
public enum AllocationBasis {
    PERCENTAGE, HEADCOUNT, REVENUE, FLOOR_AREA
}
