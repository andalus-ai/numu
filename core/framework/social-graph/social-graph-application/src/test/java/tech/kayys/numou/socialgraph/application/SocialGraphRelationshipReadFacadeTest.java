package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipListEntry;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPage;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipVisibilityPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialGraphRelationshipReadFacadeTest {
    private final TenantId tenant = TenantId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "sub-1");
    private final PartyId viewer = PartyId.generate();
    private final Instant now = Instant.parse("2026-11-17T00:00:00Z");

    @Test
    void derivesViewerFromTenantScopedIdentityAndReadsOwnFollowingList() {
        AtomicReference<PartyId> queriedOwner = new AtomicReference<>();
        SocialGraphRelationshipReadFacade facade = facade(
                (t, s) -> {
                    assertThat(t).isEqualTo(tenant);
                    assertThat(s).isEqualTo(subject);
                    return Uni.createFrom().item(Optional.of(new SocialGraphActor(tenant, viewer)));
                },
                (t, owner, at, cursor, limit) -> {
                    queriedOwner.set(owner);
                    return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
                },
                (t, v, profile) -> Uni.createFrom().item(true));

        var page = facade.following(tenant, subject, null, 20).await().indefinitely();

        assertThat(page.items()).isEmpty();
        assertThat(queriedOwner).hasValue(viewer);
    }

    @Test
    void derivesViewerSeparatelyFromRequestedFollowerListOwner() {
        PartyId profileOwner = PartyId.generate();
        AtomicReference<PartyId> queriedOwner = new AtomicReference<>();
        AtomicReference<PartyId> visibilityViewer = new AtomicReference<>();
        SocialGraphRelationshipReadFacade facade = facade(
                (t, s) -> Uni.createFrom().item(Optional.of(new SocialGraphActor(tenant, viewer))),
                (t, owner, at, cursor, limit) -> {
                    queriedOwner.set(owner);
                    return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
                },
                (t, v, profile) -> {
                    visibilityViewer.set(v);
                    return Uni.createFrom().item(true);
                });

        facade.followers(tenant, subject, profileOwner, null, 20).await().indefinitely();

        assertThat(queriedOwner).hasValue(profileOwner);
        assertThat(visibilityViewer).hasValue(viewer);
    }

    @Test
    void deniesMissingIdentityAssociationBeforeQuerying() {
        AtomicReference<Boolean> queried = new AtomicReference<>(false);
        SocialGraphRelationshipReadFacade facade = facade(
                (t, s) -> Uni.createFrom().item(Optional.empty()),
                (t, owner, at, cursor, limit) -> {
                    queried.set(true);
                    return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
                },
                (t, v, profile) -> Uni.createFrom().item(true));

        assertThatThrownBy(() -> facade.following(tenant, subject, null, 20).await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(queried.get()).isFalse();
    }

    @Test
    void rejectsResolverResultFromAnotherTenantBeforeReading() {
        AtomicReference<Boolean> queried = new AtomicReference<>(false);
        SocialGraphRelationshipReadFacade facade = facade(
                (t, s) -> Uni.createFrom().item(Optional.of(
                        new SocialGraphActor(TenantId.generate(), viewer))),
                (t, owner, at, cursor, limit) -> {
                    queried.set(true);
                    return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
                },
                (t, v, profile) -> Uni.createFrom().item(true));

        assertThatThrownBy(() -> facade.following(tenant, subject, null, 20).await().indefinitely())
                .isInstanceOf(SocialGraphAccessDeniedException.class);
        assertThat(queried.get()).isFalse();
    }

    private SocialGraphRelationshipReadFacade facade(
            SocialGraphActorResolver actors,
            Query query,
            Visibility visibility) {
        SocialGraphRelationshipQueryPort queries = new SocialGraphRelationshipQueryPort() {
            @Override
            public Uni<SocialGraphRelationshipQueryPage> findFollowing(
                    TenantId t, PartyId owner, Instant at, String cursor, int limit) {
                return query.find(t, owner, at, cursor, limit);
            }
            @Override
            public Uni<SocialGraphRelationshipQueryPage> findFollowers(
                    TenantId t, PartyId owner, Instant at, String cursor, int limit) {
                return query.find(t, owner, at, cursor, limit);
            }
        };
        SocialGraphRelationshipVisibilityPort visibilityPort = (t, v, profile) ->
                visibility.canView(t, v, profile);
        SocialGraphRelationshipPolicyPort policies = new SocialGraphRelationshipPolicyPort() {
            @Override public Uni<Boolean> isBlocked(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(false);
            }
            @Override public Uni<Boolean> isMuted(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(false);
            }
        };
        var policyService = new SocialGraphPolicyService(policies, Clock.fixed(now, ZoneOffset.UTC));
        var readService = new SocialGraphRelationshipReadService(
                queries, visibilityPort, policyService, Clock.fixed(now, ZoneOffset.UTC));
        return new SocialGraphRelationshipReadFacade(actors, readService);
    }

    @FunctionalInterface
    private interface Query {
        Uni<SocialGraphRelationshipQueryPage> find(
                TenantId tenant, PartyId owner, Instant at, String cursor, int limit);
    }

    @FunctionalInterface
    private interface Visibility {
        Uni<Boolean> canView(TenantId tenant, PartyId viewer, PartyId profile);
    }
}
