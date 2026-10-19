package tech.kayys.numou.crm.domain.valueobject;

/**
 * Stages of a sales opportunity.
 */
public enum OpportunityStage {
    PROSPECTING("Prospecting - initial contact"),
    QUALIFICATION("Qualification - assessing fit"),
    NEEDS_ANALYSIS("Needs Analysis - understanding requirements"),
    PROPOSAL("Proposal - presenting solution"),
    NEGOTIATION("Negotiation - discussing terms"),
    CLOSING("Closing - finalizing deal"),
    WON("Won - deal closed"),
    LOST("Lost - deal lost"),
    ON_HOLD("On Hold - paused");

    private final String description;

    OpportunityStage(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return this != WON && this != LOST;
    }

    public boolean isWinnable() {
        return this != WON && this != LOST && this != ON_HOLD;
    }

    public double getProbability() {
        return switch (this) {
            case PROSPECTING -> 0.1;
            case QUALIFICATION -> 0.2;
            case NEEDS_ANALYSIS -> 0.3;
            case PROPOSAL -> 0.5;
            case NEGOTIATION -> 0.7;
            case CLOSING -> 0.9;
            default -> 0.0;
        };
    }
}