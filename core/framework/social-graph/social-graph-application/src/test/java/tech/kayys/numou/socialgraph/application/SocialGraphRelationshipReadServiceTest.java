package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipListEntry;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPage;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipVisibilityPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SocialGraphRelationshipReadServiceTest {
    @Test
    void hidesPrivateProfilesAndEitherSideOfBlockedPairsWithoutChangingCursor() {
        TenantId tenant = TenantId.generate();
        PartyId viewer = PartyId.generate();
        PartyId visible = PartyId.generate();
        PartyId privateParty = PartyId.generate();
        PartyId blocked = PartyId.generate();
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        String cursor = "opaque-next-cursor";
        SocialGraphRelationshipQueryPort queries = new SocialGraphRelationshipQueryPort() {
            @Override
            public Uni<SocialGraphRelationshipQueryPage> findFollowing(TenantId t, PartyId p, Instant at, String c, int l) {
                return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(
                        new SocialGraphRelationshipListEntry(UUID.randomUUID(), visible, now),
                        new SocialGraphRelationshipListEntry(UUID.randomUUID(), privateParty, now.minusSeconds(1)),
                        new SocialGraphRelationshipListEntry(UUID.randomUUID(), blocked, now.minusSeconds(2))), cursor));
            }
            @Override
            public Uni<SocialGraphRelationshipQueryPage> findFollowers(TenantId t, PartyId p, Instant at, String c, int l) {
                return findFollowing(t, p, at, c, l);
            }
        };
        SocialGraphRelationshipVisibilityPort visibility = (t, v, subject) ->
                Uni.createFrom().item(!subject.equals(privateParty));
        SocialGraphRelationshipPolicyPort policy = new SocialGraphRelationshipPolicyPort() {
            @Override public Uni<Boolean> isBlocked(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(b.equals(blocked));
            }
            @Override public Uni<Boolean> isMuted(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(false);
            }
        };
        SocialGraphPolicyService policies = new SocialGraphPolicyService(policy, Clock.fixed(now, ZoneOffset.UTC));
        var service = new SocialGraphRelationshipReadService(
                queries, visibility, policies, Clock.fixed(now, ZoneOffset.UTC));

        var page = service.following(tenant, viewer, null, 20).await().indefinitely();

        assertThat(page.items()).extracting(SocialGraphRelationshipReadEntry::partyId).containsExactly(visible);
        assertThat(page.nextCursor()).isEqualTo(cursor);
    }

    @Test
    void doesNotQueryFollowersWhenViewerIsBlockedByProfileOwner() {
        TenantId tenant = TenantId.generate();
        PartyId viewer = PartyId.generate();
        PartyId owner = PartyId.generate();
        java.util.concurrent.atomic.AtomicBoolean queried = new java.util.concurrent.atomic.AtomicBoolean();
        SocialGraphRelationshipQueryPort queries = new SocialGraphRelationshipQueryPort() {
            @Override public Uni<SocialGraphRelationshipQueryPage> findFollowing(
                    TenantId t, PartyId p, Instant at, String c, int l) {
                queried.set(true);
                return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
            }
            @Override public Uni<SocialGraphRelationshipQueryPage> findFollowers(
                    TenantId t, PartyId p, Instant at, String c, int l) {
                queried.set(true);
                return Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null));
            }
        };
        SocialGraphRelationshipVisibilityPort visibility = (t, v, subject) -> Uni.createFrom().item(true);
        SocialGraphPolicyService policies = new SocialGraphPolicyService(new SocialGraphRelationshipPolicyPort() {
            @Override public Uni<Boolean> isBlocked(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(a.equals(viewer) && b.equals(owner));
            }
            @Override public Uni<Boolean> isMuted(TenantId t, PartyId a, PartyId b, Instant at) {
                return Uni.createFrom().item(false);
            }
        }, Clock.systemUTC());
        var service = new SocialGraphRelationshipReadService(queries, visibility, policies, Clock.systemUTC());

        var page = service.followers(tenant, viewer, owner, null, 20).await().indefinitely();

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(queried).isFalse();
    }

    @Test
    void rejectsUnboundedPageSizes() {
        var service = new SocialGraphRelationshipReadService(
                (t, p, at, c, l) -> Uni.createFrom().item(new SocialGraphRelationshipQueryPage(List.of(), null)),
                (t, v, p) -> Uni.createFrom().item(true),
                new SocialGraphPolicyService(new SocialGraphRelationshipPolicyPort() {
                    @Override public Uni<Boolean> isBlocked(TenantId t, PartyId a, PartyId b, Instant at) { return Uni.createFrom().item(false); }
                    @Override public Uni<Boolean> isMuted(TenantId t, PartyId a, PartyId b, Instant at) { return Uni.createFrom().item(false); }
                }, Clock.systemUTC()), Clock.systemUTC());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.followers(TenantId.generate(), PartyId.generate(), PartyId.generate(), null, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
