package tech.kayys.numou.crm.domain.valueobject;

/**
 * Status of a lead.
 */
public enum LeadStatus {
    NEW("New - recently created"),
    CONTACTED("Contacted - outreach made"),
    QUALIFIED("Qualified - viable prospect"),
    CONVERTED("Converted - became customer"),
    LOST("Lost - not interested"),
    NURTURING("Nurturing - building relationship"),
    UNQUALIFIED("Unqualified - not a fit"),
    ARCHIVED("Archived - no longer active");

    private final String description;

    LeadStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this != CONVERTED && this != LOST && this != ARCHIVED;
    }

    public boolean isQualified() {
        return this == QUALIFIED || this == NURTURING;
    }

    public boolean canTransitionTo(LeadStatus target) {
        return switch (this) {
            case NEW -> target == CONTACTED || target == QUALIFIED || target == LOST || target == ARCHIVED;
            case CONTACTED -> target == QUALIFIED || target == NURTURING || target == LOST || target == ARCHIVED;
            case QUALIFIED -> target == CONVERTED || target == NURTURING || target == LOST;
            case NURTURING -> target == QUALIFIED || target == CONVERTED || target == LOST;
            case CONVERTED, LOST, ARCHIVED, UNQUALIFIED -> false;
        };
    }
}