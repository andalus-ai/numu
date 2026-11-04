package tech.kayys.numou.socialgraph.application;

/**
 * Host-owned authorization boundary for Social Graph commands.
 *
 * Implementations must verify that the authenticated actor is an active member
 * of the actor's tenant and is permitted to use Social Graph in that tenant.
 * This is deliberately separate from identity-to-Party resolution: a Party
 * association establishes identity, not authorization to invoke every capability.
 */
@FunctionalInterface
public interface SocialGraphAuthorizationPort {
    /**
     * Returns normally only when the authenticated actor is authorized.
     *
     * @throws SocialGraphAccessDeniedException when tenant membership or capability
     *         authorization is absent, inactive, or insufficient
     */
    void requireAuthorized(SocialGraphActor actor);
}
