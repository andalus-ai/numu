package tech.kayys.numou.workforce.domain.employment;

/**
 * Status of an employment relationship.
 */
public enum EmploymentStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
