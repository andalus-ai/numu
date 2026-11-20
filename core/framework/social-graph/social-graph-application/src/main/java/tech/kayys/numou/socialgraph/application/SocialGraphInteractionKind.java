package tech.kayys.numou.socialgraph.application;

/**
 * Consumer intent evaluated against authoritative Social Graph relationship policy.
 * MUTE is deliberately relevant only to delivery intents, never to direct actions.
 */
public enum SocialGraphInteractionKind {
    FOLLOW,
    MESSAGE,
    COMMENT,
    INVITE,
    ENDORSE,
    FEED_DELIVERY,
    NOTIFICATION_DELIVERY;

    public boolean isDelivery() {
        return this == FEED_DELIVERY || this == NOTIFICATION_DELIVERY;
    }
}
