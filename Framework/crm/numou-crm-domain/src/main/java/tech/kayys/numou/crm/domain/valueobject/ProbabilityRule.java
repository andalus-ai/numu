package tech.kayys.numou.crm.domain.valueobject;

/**
 * Probability rules for opportunity stages.
 */
public enum ProbabilityRule {
    PROSPECTING(0.1, "Early stage - low probability"),
    QUALIFICATION(0.2, "Qualifying - moderate probability"),
    NEEDS_ANALYSIS(0.3, "Needs identified - increasing probability"),
    PROPOSAL(0.5, "Proposal presented - good probability"),
    NEGOTIATION(0.7, "Negotiating - high probability"),
    CLOSING(0.9, "Closing - very high probability"),
    WON(1.0, "Won - deal closed"),
    LOST(0.0, "Lost - deal closed"),
    ON_HOLD(0.3, "On hold - paused");

    private final double probability;
    private final String description;

    ProbabilityRule(double probability, String description) {
        this.probability = probability;
        this.description = description;
    }

    public double getProbability() {
        return probability;
    }

    public String getDescription() {
        return description;
    }

    public boolean isWinnable() {
        return this != WON && this != LOST;
    }

    public boolean isActive() {
        return this != WON && this != LOST;
    }

    public boolean canTransitionTo(ProbabilityRule target) {
        return switch (this) {
            case PROSPECTING -> target == QUALIFICATION || target == LOST || target == ON_HOLD;
            case QUALIFICATION -> target == NEEDS_ANALYSIS || target == LOST || target == ON_HOLD;
            case NEEDS_ANALYSIS -> target == PROPOSAL || target == LOST || target == ON_HOLD;
            case PROPOSAL -> target == NEGOTIATION || target == LOST || target == ON_HOLD;
            case NEGOTIATION -> target == CLOSING || target == LOST || target == ON_HOLD;
            case CLOSING -> target == WON || target == LOST;
            case ON_HOLD -> target == PROSPECTING || target == QUALIFICATION || 
                           target == NEEDS_ANALYSIS || target == PROPOSAL || 
                           target == NEGOTIATION || target == CLOSING || target == LOST;
            case WON, LOST -> false;
        };
    }
}