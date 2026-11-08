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
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;

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
        SocialGraphCommandInvoker invoker = new NoOpInvoker() {
            @Override
            public Uni<SocialConnection> acceptConnection(TenantId actualTenant, IdentitySubjectRef actualSubject,
                    SocialConnectionId id, String key) {
                assertThat(actualTenant).isEqualTo(tenant);
                assertThat(actualSubject).isEqualTo(subject);
                invoked.set(true);
                return Uni.createFrom().failure(new UnsupportedOperationException("test sentinel"));
            }
        };

        assertThatThrownBy(() -> gateway(context(tenant), Optional.of(subject), invoker)
                .acceptConnection(SocialConnectionId.generate(), "key-1").await().indefinitely())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(invoked).isTrue();
    }

    @Test
    void deniesMissingSecurityContextBeforeResolvingSubject() {
        AtomicBoolean subjectRead = new AtomicBoolean();
        VerifiedIdentitySubjectProvider provider = () -> {
            subjectRead.set(true);
            return Optional.of(subject);
        };

        assertDenied(gateway(null, provider, new NoOpInvoker()));
        assertThat(subjectRead).isFalse();
    }

    @Test
    void deniesMissingVerifiedSubject() {
        assertDenied(gateway(context(tenant), Optional.empty(), new NoOpInvoker()));
    }

    @Test
    void deniesPrincipalNotMemberOfActiveTenant() {
        var principal = new Principal(PrincipalId.generate(), "test", Set.of());
        var context = new SecurityContext(principal, tenant,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                Set.of(Permission.of("social-graph", "perform")));
        assertDenied(gateway(context, Optional.of(subject), new NoOpInvoker()));
    }

    private void assertDenied(AuthenticatedSocialGraphCommandGateway gateway) {
        assertThatThrownBy(() -> gateway.acceptConnection(SocialConnectionId.generate(), "key")
                .await().indefinitely()).isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    private SecurityContext context(TenantId tenantId) {
        return new SecurityContext(Principal.of(PrincipalId.generate(), "test", tenantId), tenantId,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                Set.of(Permission.of("social-graph", "perform")));
    }

    private AuthenticatedSocialGraphCommandGateway gateway(
            SecurityContext context, Optional<IdentitySubjectRef> identity, SocialGraphCommandInvoker invoker) {
        return gateway(context, () -> identity, invoker);
    }

    private AuthenticatedSocialGraphCommandGateway gateway(
            SecurityContext context, VerifiedIdentitySubjectProvider identity, SocialGraphCommandInvoker invoker) {
        SecurityContextProvider contexts = new SecurityContextProvider() {
            @Override public Optional<SecurityContext> current() { return Optional.ofNullable(context); }
            @Override public SecurityContext require() { return Optional.ofNullable(context).orElseThrow(); }
        };
        return new AuthenticatedSocialGraphCommandGateway(contexts, identity, invoker);
    }

    private static class NoOpInvoker implements SocialGraphCommandInvoker {
        @Override public Uni<SocialConnection> requestConnection(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialConnection> acceptConnection(TenantId t, IdentitySubjectRef s, SocialConnectionId id, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialConnection> rejectConnection(TenantId t, IdentitySubjectRef s, SocialConnectionId id, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialConnection> cancelConnection(TenantId t, IdentitySubjectRef s, SocialConnectionId id, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialConnection> removeConnection(TenantId t, IdentitySubjectRef s, SocialConnectionId id, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> follow(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> unfollow(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> block(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> unblock(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> mute(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
        @Override public Uni<SocialRelationship> unmute(TenantId t, IdentitySubjectRef s, PartyId p, String k) { return Uni.createFrom().nullItem(); }
    }
}
