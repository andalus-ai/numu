package tech.kayys.numou.socialgraph.domain;

/** State machine for a bilateral social connection request. */
public enum SocialConnectionStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    CANCELLED,
    REMOVED
}
