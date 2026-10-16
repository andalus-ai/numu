package tech.kayys.syirkah.hris.domain.valueobject;

/**
 * Status of a training program.
 */
public enum TrainingStatus {
    PLANNED("Planned - training scheduled"),
    IN_PROGRESS("In Progress - training underway"),
    COMPLETED("Completed - training finished"),
    CANCELLED("Cancelled - training cancelled"),
    POSTPONED("Postponed - training delayed");

    private final String description;

    TrainingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == PLANNED || this == IN_PROGRESS;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
