package tech.kayys.syirkah.kiosk.domain.valueobject;

/**
 * Status of a kiosk session.
 */
public enum SessionStatus {
    STARTED("Started - Session initialized"),
    IN_PROGRESS("In Progress - Customer is shopping"),
    CHECKING_OUT("Checking Out - Processing payment"),
    COMPLETED("Completed - Transaction finished"),
    ABANDONED("Abandoned - Customer left"),
    TIMED_OUT("Timed Out - Idle timeout"),
    CANCELLED("Cancelled - Customer cancelled");

    private final String description;

    SessionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == STARTED || this == IN_PROGRESS || this == CHECKING_OUT;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED || 
               this == TIMED_OUT || this == CANCELLED;
    }
}