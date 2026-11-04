package tech.kayys.numou.security.socialgraph;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.domain.authentication.AuthenticationInfo;
import tech.kayys.numou.security.domain.authentication.AuthenticationMethod;
import tech.kayys.numou.security.domain.authorization.Permission;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.authorization.SecurityContext;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.application.SocialGraphActor;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationAction;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextSocialGraphAuthorizationAdapterTest {
    private final TenantId tenant = TenantId.generate();
    private final SocialGraphActor actor = new SocialGraphActor(tenant, PartyId.generate());

    @Test
    void allowsAuthenticatedTenantMemberWithRequestPermission() {
        var adapter = adapter(context(tenant, Set.of(Permission.of("social-graph", "create"))));

        assertThatCode(() -> adapter.requireAuthorized(actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsLifecycleCommandOnlyWithPerformPermission() {
        var adapter = adapter(context(tenant, Set.of(Permission.of("social-graph", "perform"))));

        assertThatCode(() -> adapter.requireAuthorized(actor, SocialGraphAuthorizationAction.MANAGE_CONNECTION))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> adapter.requireAuthorized(actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION))
                .isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    @Test
    void deniesMissingContextAndCrossTenantContext() {
        assertThatThrownBy(() -> adapter(null).requireAuthorized(
                actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION))
                .isInstanceOf(SocialGraphAccessDeniedException.class);

        var otherTenantContext = context(TenantId.generate(),
                Set.of(Permission.of("social-graph", "create")));
        assertThatThrownBy(() -> adapter(otherTenantContext).requireAuthorized(
                actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION))
                .isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    @Test
    void deniesPrincipalWithoutTenantMembershipEvenWhenPermissionIsPresent() {
        var principal = new Principal(PrincipalId.generate(), "test", Set.of());
        var context = new SecurityContext(principal, tenant,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                Set.of(Permission.of("social-graph", "create")));

        assertThatThrownBy(() -> adapter(context).requireAuthorized(
                actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION))
                .isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    private SecurityContext context(TenantId tenantId, Set<Permission> permissions) {
        var principal = Principal.of(PrincipalId.generate(), "test", tenantId);
        return new SecurityContext(principal, tenantId,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()), permissions);
    }

    private SecurityContextSocialGraphAuthorizationAdapter adapter(SecurityContext context) {
        return new SecurityContextSocialGraphAuthorizationAdapter(new SecurityContextProvider() {
            @Override
            public Optional<SecurityContext> current() {
                return Optional.ofNullable(context);
            }

            @Override
            public SecurityContext require() {
                return Optional.ofNullable(context)
                        .orElseThrow(SocialGraphAccessDeniedException::new);
            }
        });
    }
}
