package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
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
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;
import tech.kayys.numou.socialgraph.application.SocialGraphCommandFacade;
import tech.kayys.numou.socialgraph.application.SocialGraphService;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedSocialGraphCommandGatewayTest {
    private final TenantId tenant = TenantId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "subject-123");

    @Test
    void derivesTenantAndIdentityFromTrustedProviders() {
        AtomicBoolean invoked = new AtomicBoolean();
        SocialGraphCommandFacade facade = facade((receivedTenant, receivedSubject) -> {
            assertThat(receivedTenant).isEqualTo(tenant);
            assertThat(receivedSubject).isEqualTo(subject);
            invoked.set(true);
            return Uni.createFrom().failure(new UnsupportedOperationException("test sentinel"));
        });

        var gateway = gateway(context(tenant), Optional.of(subject), facade);
        assertThatThrownBy(() -> gateway.acceptConnection(SocialConnectionId.generate(), "key-1")
                .await().indefinitely()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(invoked).isTrue();
    }

    @Test
    void deniesMissingSecurityContextBeforeResolvingSubject() {
        AtomicBoolean subjectRead = new AtomicBoolean();
        var gateway = gateway(null, () -> {
            subjectRead.set(true);
            return Optional.of(subject);
        }, facade((tenant, identity) -> Uni.createFrom().nullItem()));

        assertDenied(gateway);
        assertThat(subjectRead).isFalse();
    }

    @Test
    void deniesMissingVerifiedSubject() {
        var gateway = gateway(context(tenant), Optional.empty(),
                facade((t, s) -> Uni.createFrom().nullItem()));
        assertDenied(gateway);
    }

    @Test
    void deniesPrincipalNotMemberOfActiveTenant() {
        var principal = new Principal(PrincipalId.generate(), "test", Set.of());
        var context = new SecurityContext(principal, tenant,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                Set.of(Permission.of("social-graph", "perform")));
        var gateway = gateway(context, Optional.of(subject),
                facade((t, s) -> Uni.createFrom().nullItem()));
        assertDenied(gateway);
    }

    private void assertDenied(AuthenticatedSocialGraphCommandGateway gateway) {
        assertThatThrownBy(() -> gateway.acceptConnection(SocialConnectionId.generate(), "key-denied")
                .await().indefinitely()).isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    private SecurityContext context(TenantId tenantId) {
        return new SecurityContext(Principal.of(PrincipalId.generate(), "test", tenantId), tenantId,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                Set.of(Permission.of("social-graph", "perform")));
    }

    private AuthenticatedSocialGraphCommandGateway gateway(
            SecurityContext context, Optional<IdentitySubjectRef> subject, SocialGraphCommandFacade facade) {
        return gateway(context, () -> subject, facade);
    }

    private AuthenticatedSocialGraphCommandGateway gateway(
            SecurityContext context, VerifiedIdentitySubjectProvider subject, SocialGraphCommandFacade facade) {
        return new AuthenticatedSocialGraphCommandGateway(new SecurityContextProvider() {
            @Override public Optional<SecurityContext> current() { return Optional.ofNullable(context); }
            @Override public SecurityContext require() { return Optional.ofNullable(context).orElseThrow(); }
        }, subject, facade);
    }

    private SocialGraphCommandFacade facade(TrustedFacadeCall call) {
        SocialGraphActorResolver resolver = (tenantId, identity) ->
                Uni.createFrom().item(Optional.of(new SocialGraphActor(tenantId, PartyId.generate())));
        SocialGraphService service = new SocialGraphService(
                work -> Uni.createFrom().failure(new UnsupportedOperationException("not used")),
                partyId -> Uni.createFrom().item(true),
                (actor, action) -> { },
                Clock.systemUTC());
        return new SocialGraphCommandFacade(resolver, service) {
            @Override
            public Uni<SocialConnection> acceptConnection(
                    TenantId tenantId, IdentitySubjectRef identity, SocialConnectionId id, String key) {
                return call.execute(tenantId, identity);
            }
        };
    }

    @FunctionalInterface
    private interface TrustedFacadeCall {
        Uni<SocialConnection> execute(TenantId tenant, IdentitySubjectRef subject);
    }
}
