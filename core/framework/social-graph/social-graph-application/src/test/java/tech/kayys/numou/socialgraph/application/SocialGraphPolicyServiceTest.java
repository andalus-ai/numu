package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SocialGraphPolicyServiceTest {
    @Test
    void blockIsSymmetricWhileMuteIsViewerDirectionalAndFeedSuppressionCombinesBoth() {
        TenantId tenant = TenantId.generate();
        PartyId viewer = PartyId.generate();
        PartyId author = PartyId.generate();
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        AtomicReference<PartyId> blockFirst = new AtomicReference<>();
        AtomicReference<PartyId> blockSecond = new AtomicReference<>();
        AtomicReference<PartyId> muteViewer = new AtomicReference<>();
        AtomicReference<PartyId> muteAuthor = new AtomicReference<>();

        SocialGraphRelationshipPolicyPort port = new SocialGraphRelationshipPolicyPort() {
            @Override
            public Uni<Boolean> isBlocked(TenantId scope, PartyId first, PartyId second, Instant at) {
                blockFirst.set(first);
                blockSecond.set(second);
                return Uni.createFrom().item(false);
            }

            @Override
            public Uni<Boolean> isMuted(TenantId scope, PartyId actor, PartyId target, Instant at) {
                muteViewer.set(actor);
                muteAuthor.set(target);
                return Uni.createFrom().item(actor.equals(viewer) && target.equals(author));
            }
        };
        SocialGraphPolicyService service = new SocialGraphPolicyService(
                port, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.canInteract(tenant, viewer, author).await().indefinitely()).isTrue();
        assertThat(blockFirst.get()).isEqualTo(viewer);
        assertThat(blockSecond.get()).isEqualTo(author);

        assertThat(service.isMuted(tenant, viewer, author).await().indefinitely()).isTrue();
        assertThat(muteViewer.get()).isEqualTo(viewer);
        assertThat(muteAuthor.get()).isEqualTo(author);
        assertThat(service.isMuted(tenant, author, viewer).await().indefinitely()).isFalse();
        assertThat(service.shouldSuppressFromFeed(tenant, viewer, author).await().indefinitely()).isTrue();
    }
}
