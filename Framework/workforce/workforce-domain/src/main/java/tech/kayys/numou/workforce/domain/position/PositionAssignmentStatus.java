package tech.kayys.numou.workforce.domain.position;

/**
 * Status of a position assignment.
 */
public enum PositionAssignmentStatus {
    ACTIVE,
    ENDED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
