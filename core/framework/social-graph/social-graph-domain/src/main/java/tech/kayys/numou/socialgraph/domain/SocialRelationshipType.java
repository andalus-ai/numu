package tech.kayys.numou.socialgraph.domain;

/**
 * Stable built-in relationship semantics. Connection requests remain in the
 * dedicated SocialConnection aggregate because their approval state machine
 * is bilateral; these types represent independently managed directed edges.
 */
public enum SocialRelationshipType {
    FOLLOW(true),
    BLOCK(true),
    MUTE(true);

    private final boolean directed;

    SocialRelationshipType(boolean directed) {
        this.directed = directed;
    }

    public boolean directed() {
        return directed;
    }
}
