package tech.kayys.numou.socialgraph.application;

/**
 * Host-owned authorization boundary for Social Graph commands.
 *
 * Implementations must validate the authenticated tenant membership and the
 * permission for the requested command. Identity-to-Party association alone
 * is not authorization to invoke every capability operation.
 */
@FunctionalInterface
public interface SocialGraphAuthorizationPort {
    /**
     * Returns normally only when the authenticated actor is authorized.
     *
     * @throws SocialGraphAccessDeniedException when tenant membership or the
     *         requested capability permission is absent or insufficient
     */
    void requireAuthorized(SocialGraphActor actor, SocialGraphAuthorizationAction action);
}
