package tech.kayys.numou.socialgraph.application;

/** Result of applying Social Graph block and mute policy to a consumer intent. */
public enum SocialGraphInteractionDecision {
    ALLOWED,
    BLOCKED,
    MUTED
}
