package tech.kayys.numou.socialgraph.application;

/**
 * Host-owned authorization boundary for Social Graph commands.
 * Implementations verify active tenant membership and permission to use this capability.
 * Identity-to-Party association alone is not authorization.
 */
@FunctionalInterface
public interface SocialGraphAuthorizationPort {
    /** Returns normally only when the authenticated actor is authorized. */
    void requireAuthorized(SocialGraphActor actor);
}
