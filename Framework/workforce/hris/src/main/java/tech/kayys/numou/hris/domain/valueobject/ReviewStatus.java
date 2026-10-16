package tech.kayys.syirkah.hris.domain.valueobject;

/**
 * Status of a performance review.
 */
public enum ReviewStatus {
    SCHEDULED("Scheduled - review planned"),
    IN_PROGRESS("In Progress - review ongoing"),
    COMPLETED("Completed - review finished"),
    CANCELLED("Cancelled - review cancelled"),
    PENDING_APPROVAL("Pending Approval - awaiting final approval"),
    APPROVED("Approved - review approved");

    private final String description;

    ReviewStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == SCHEDULED || this == IN_PROGRESS || this == PENDING_APPROVAL;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == APPROVED || this == CANCELLED;
    }

    public boolean canTransitionTo(ReviewStatus target) {
        return switch (this) {
            case SCHEDULED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == PENDING_APPROVAL || target == COMPLETED || target == CANCELLED;
            case PENDING_APPROVAL -> target == APPROVED || target == CANCELLED;
            case APPROVED, COMPLETED, CANCELLED -> false;
        };
    }
}
