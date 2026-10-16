package tech.kayys.syirkah.purchasing.domain.valueobject;

/**
 * Contract status enumeration representing the lifecycle states.
 */
public enum ContractStatus {
    DRAFT("Draft", "Contract is being prepared"),
    PENDING_REVIEW("Pending Review", "Awaiting legal or management review"),
    PENDING_APPROVAL("Pending Approval", "Awaiting final approval"),
    PENDING_SIGNATURE("Pending Signature", "Awaiting signatures from parties"),
    ACTIVE("Active", "Contract is in effect"),
    SUSPENDED("Suspended", "Contract temporarily suspended"),
    EXPIRED("Expired", "Contract has expired"),
    TERMINATED("Terminated", "Contract terminated before expiration"),
    COMPLETED("Completed", "Contract obligations fulfilled"),
    CANCELLED("Cancelled", "Contract cancelled before activation"),
    RENEWED("Renewed", "Contract has been renewed");

    private final String displayName;
    private final String description;

    ContractStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this == ACTIVE || this == SUSPENDED || this == RENEWED;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED || this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(ContractStatus target) {
        if (this == target) {
            return false;
        }
        
        switch (this) {
            case DRAFT:
                return target == PENDING_REVIEW || target == CANCELLED;
            case PENDING_REVIEW:
                return target == DRAFT || target == PENDING_APPROVAL || target == CANCELLED;
            case PENDING_APPROVAL:
                return target == DRAFT || target == PENDING_SIGNATURE || target == CANCELLED;
            case PENDING_SIGNATURE:
                return target == ACTIVE || target == CANCELLED;
            case ACTIVE:
                return target == SUSPENDED || target == EXPIRED || target == TERMINATED || target == COMPLETED || target == RENEWED;
            case SUSPENDED:
                return target == ACTIVE || target == TERMINATED;
            case EXPIRED:
                return target == RENEWED;
            case TERMINATED:
            case COMPLETED:
            case CANCELLED:
                return false;
            case RENEWED:
                return target == EXPIRED || target == TERMINATED;
            default:
                return false;
        }
    }
}
