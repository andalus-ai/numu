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
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedSocialGraphRelationshipReadGatewayTest {
    private final TenantId tenant = TenantId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "subject-123");

    @Test
    void followingUsesTenantAndSubjectFromTrustedProviders() {
        AtomicBoolean invoked = new AtomicBoolean();
        SocialGraphRelationshipReadInvoker invoker = new NoOpReadInvoker() {
            @Override
            public Uni<SocialGraphRelationshipReadPage> following(
                    TenantId actualTenant, IdentitySubjectRef actualSubject, String cursor, int limit) {
                assertThat(actualTenant).isEqualTo(tenant);
                assertThat(actualSubject).isEqualTo(subject);
                assertThat(cursor).isEqualTo("cursor-1");
                assertThat(limit).isEqualTo(25);
                invoked.set(true);
                return Uni.createFrom().item(new SocialGraphRelationshipReadPage(List.of(), null));
            }
        };

        var page = gateway(context(tenant, true), Optional.of(subject), invoker)
                .following("cursor-1", 25).await().indefinitely();

        assertThat(page.items()).isEmpty();
        assertThat(invoked).isTrue();
    }

    @Test
    void followersKeepsProfileOwnerAsTargetNotViewerIdentity() {
        PartyId profileOwner = PartyId.generate();
        AtomicBoolean invoked = new AtomicBoolean();
        SocialGraphRelationshipReadInvoker invoker = new NoOpReadInvoker() {
            @Override
            public Uni<SocialGraphRelationshipReadPage> followers(
                    TenantId actualTenant, IdentitySubjectRef actualSubject,
                    PartyId actualOwner, String cursor, int limit) {
                assertThat(actualTenant).isEqualTo(tenant);
                assertThat(actualSubject).isEqualTo(subject);
                assertThat(actualOwner).isEqualTo(profileOwner);
                invoked.set(true);
                return Uni.createFrom().item(new SocialGraphRelationshipReadPage(List.of(), null));
            }
        };

        gateway(context(tenant, true), Optional.of(subject), invoker)
                .followers(profileOwner, null, 20).await().indefinitely();

        assertThat(invoked).isTrue();
    }

    @Test
    void deniesMissingReadPermissionBeforeReadingVerifiedSubject() {
        AtomicBoolean subjectRead = new AtomicBoolean();
        var provider = (VerifiedIdentitySubjectProvider) () -> {
            subjectRead.set(true);
            return Optional.of(subject);
        };

        assertDenied(gateway(context(tenant, false), provider, new NoOpReadInvoker()));
        assertThat(subjectRead).isFalse();
    }

    @Test
    void deniesMissingVerifiedSubject() {
        assertDenied(gateway(context(tenant, true), Optional.empty(), new NoOpReadInvoker()));
    }

    @Test
    void deniesMissingSecurityContext() {
        assertDenied(gateway(null, Optional.of(subject), new NoOpReadInvoker()));
    }

    private void assertDenied(AuthenticatedSocialGraphRelationshipReadGateway gateway) {
        assertThatThrownBy(() -> gateway.following(null, 10).await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
    }

    private SecurityContext context(TenantId tenantId, boolean readPermission) {
        var permissions = readPermission ? java.util.Set.of(Permission.of("social-graph", "read")) : java.util.Set.<Permission>of();
        return new SecurityContext(Principal.of(PrincipalId.generate(), "test", tenantId), tenantId,
                AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()), permissions);
    }

    private AuthenticatedSocialGraphRelationshipReadGateway gateway(
            SecurityContext context, Optional<IdentitySubjectRef> identity, SocialGraphRelationshipReadInvoker invoker) {
        return gateway(context, () -> identity, invoker);
    }

    private AuthenticatedSocialGraphRelationshipReadGateway gateway(
            SecurityContext context, VerifiedIdentitySubjectProvider identity, SocialGraphRelationshipReadInvoker invoker) {
        SecurityContextProvider contexts = new SecurityContextProvider() {
            @Override public Optional<SecurityContext> current() { return Optional.ofNullable(context); }
            @Override public SecurityContext require() { return Optional.ofNullable(context).orElseThrow(); }
        };
        return new AuthenticatedSocialGraphRelationshipReadGateway(contexts, identity, invoker);
    }

    private static class NoOpReadInvoker implements SocialGraphRelationshipReadInvoker {
        @Override
        public Uni<SocialGraphRelationshipReadPage> following(
                TenantId tenant, IdentitySubjectRef subject, String cursor, int limit) {
            return Uni.createFrom().item(new SocialGraphRelationshipReadPage(List.of(), null));
        }
        @Override
        public Uni<SocialGraphRelationshipReadPage> followers(
                TenantId tenant, IdentitySubjectRef subject, PartyId owner, String cursor, int limit) {
            return Uni.createFrom().item(new SocialGraphRelationshipReadPage(List.of(), null));
        }
    }
}
