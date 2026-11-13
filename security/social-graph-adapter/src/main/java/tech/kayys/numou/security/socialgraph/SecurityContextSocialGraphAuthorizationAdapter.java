package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.SecurityContext;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.application.SocialGraphActor;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationAction;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationPort;

import java.util.Objects;

/**
 * Enforces Social Graph command permissions using the request's verified
 * Numou SecurityContext. It deliberately does not resolve principal IDs to
 * Party IDs; that identity association belongs to the trusted actor resolver.
 */
@ApplicationScoped
public class SecurityContextSocialGraphAuthorizationAdapter implements SocialGraphAuthorizationPort {
    private static final Permission REQUEST_CONNECTION = Permission.of("social-graph", "create");
    private static final Permission MANAGE_CONNECTION = Permission.of("social-graph", "perform");

    private final SecurityContextProvider securityContexts;

    @Inject
    public SecurityContextSocialGraphAuthorizationAdapter(SecurityContextProvider securityContexts) {
        this.securityContexts = Objects.requireNonNull(securityContexts, "securityContexts cannot be null");
    }

    @Override
    public void requireAuthorized(SocialGraphActor actor, SocialGraphAuthorizationAction action) {
        if (actor == null || action == null) throw new SocialGraphAccessDeniedException();
        var current = securityContexts.current();
        SecurityContext context = current == null ? null : current.orElse(null);
        Permission required = switch (action) {
            case REQUEST_CONNECTION -> REQUEST_CONNECTION;
            case MANAGE_CONNECTION, MANAGE_RELATIONSHIP -> MANAGE_CONNECTION;
        };
        if (context == null || !context.authenticated()
                || !actor.tenantId().equals(context.tenantId())
                || !context.tenantMember()
                || !context.hasPermission(required)) {
            throw new SocialGraphAccessDeniedException();
        }
    }
}
