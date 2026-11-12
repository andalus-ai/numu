package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocialGraphInteractionPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void blockDeniesEveryIntentBeforeMuteIsConsulted() {
        TenantId tenant = TenantId.generate();
        PartyId actor = PartyId.generate();
        PartyId target = PartyId.generate();
        List<String> calls = new ArrayList<>();
        var service = service((scope, first, second, at) -> {
            calls.add("block");
            assertThat(scope).isEqualTo(tenant);
            assertThat(at).isEqualTo(NOW);
            return Uni.createFrom().item(true);
        }, (scope, viewer, author, at) -> {
            calls.add("mute");
            return Uni.createFrom().item(true);
        });

        var result = service.evaluate(tenant, actor, target, SocialGraphInteractionKind.MESSAGE)
                .await().indefinitely();

        assertThat(result).isEqualTo(SocialGraphInteractionDecision.BLOCKED);
        assertThat(calls).containsExactly("block");
    }

    @Test
    void muteSuppressesDeliveryButDoesNotDenyDirectInteractions() {
        TenantId tenant = TenantId.generate();
        PartyId viewer = PartyId.generate();
        PartyId author = PartyId.generate();
        List<String> muteCalls = new ArrayList<>();
        var service = service((scope, first, second, at) -> Uni.createFrom().item(false),
                (scope, actor, target, at) -> {
                    muteCalls.add(actor + "->" + target);
                    assertThat(at).isEqualTo(NOW);
                    return Uni.createFrom().item(actor.equals(viewer) && target.equals(author));
                });

        assertThat(service.evaluate(tenant, viewer, author, SocialGraphInteractionKind.COMMENT)
                .await().indefinitely()).isEqualTo(SocialGraphInteractionDecision.ALLOWED);
        assertThat(muteCalls).isEmpty();

        assertThat(service.evaluate(tenant, viewer, author, SocialGraphInteractionKind.FEED_DELIVERY)
                .await().indefinitely()).isEqualTo(SocialGraphInteractionDecision.MUTED);
        assertThat(service.evaluate(tenant, author, viewer, SocialGraphInteractionKind.NOTIFICATION_DELIVERY)
                .await().indefinitely()).isEqualTo(SocialGraphInteractionDecision.ALLOWED);
    }

    @Test
    void selfInteractionDoesNotQueryRelationshipStore() {
        TenantId tenant = TenantId.generate();
        PartyId self = PartyId.generate();
        var service = service((scope, first, second, at) -> {
            throw new AssertionError("self policy should not query block store");
        }, (scope, viewer, author, at) -> {
            throw new AssertionError("self policy should not query mute store");
        });

        assertThat(service.evaluate(tenant, self, self, SocialGraphInteractionKind.MESSAGE)
                .await().indefinitely()).isEqualTo(SocialGraphInteractionDecision.ALLOWED);
    }

    private static SocialGraphInteractionPolicyService service(
            BlockLookup block, MuteLookup mute) {
        SocialGraphRelationshipPolicyPort port = new SocialGraphRelationshipPolicyPort() {
            @Override
            public Uni<Boolean> isBlocked(TenantId tenant, PartyId first, PartyId second, Instant at) {
                return block.lookup(tenant, first, second, at);
            }

            @Override
            public Uni<Boolean> isMuted(TenantId tenant, PartyId viewer, PartyId author, Instant at) {
                return mute.lookup(tenant, viewer, author, at);
            }
        };
        return new SocialGraphInteractionPolicyService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @FunctionalInterface
    private interface BlockLookup {
        Uni<Boolean> lookup(TenantId tenant, PartyId first, PartyId second, Instant at);
    }

    @FunctionalInterface
    private interface MuteLookup {
        Uni<Boolean> lookup(TenantId tenant, PartyId viewer, PartyId author, Instant at);
    }
}
