package tech.kayys.numou.notification.adapter.socialgraph;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.notification.application.port.NotificationDeliveryPolicy;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.socialgraph.application.SocialGraphInteractionPolicyService;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SocialGraphNotificationDeliveryPolicyAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");

    @Test
    void recipientMuteIsEvaluatedInRecipientToAuthorDirection() {
        UUID tenant = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        PartyId recipientParty = PartyId.generate();
        PartyId authorParty = PartyId.generate();
        AtomicReference<PartyId> seenViewer = new AtomicReference<>();
        AtomicReference<PartyId> seenAuthor = new AtomicReference<>();
        SocialGraphRelationshipPolicyPort port = new SocialGraphRelationshipPolicyPort() {
            @Override
            public Uni<Boolean> isBlocked(TenantId scope, PartyId first, PartyId second, Instant at) {
                assertThat(scope.value()).isEqualTo(tenant);
                assertThat(at).isEqualTo(NOW);
                return Uni.createFrom().item(false);
            }

            @Override
            public Uni<Boolean> isMuted(TenantId scope, PartyId viewer, PartyId author, Instant at) {
                seenViewer.set(viewer);
                seenAuthor.set(author);
                return Uni.createFrom().item(true);
            }
        };
        var service = new SocialGraphInteractionPolicyService(port, Clock.fixed(NOW, ZoneOffset.UTC));
        var adapter = new SocialGraphNotificationDeliveryPolicyAdapter(service,
                (scope, id) -> Optional.of(recipientParty));
        Notification notification = Notification.create(tenant, recipientId, "social.reply",
                "New reply", "A person replied", "post:1", "key-1", authorParty.value(), NOW);

        assertThat(adapter.evaluate(notification)).isEqualTo(NotificationDeliveryPolicy.Decision.MUTED);
        assertThat(seenViewer.get()).isEqualTo(recipientParty);
        assertThat(seenAuthor.get()).isEqualTo(authorParty);
    }

    @Test
    void unresolvedRecipientPartyFailsClosed() {
        UUID tenant = UUID.randomUUID();
        Notification notification = Notification.create(tenant, UUID.randomUUID(), "social.reply",
                "New reply", "A person replied", "post:1", "key-2", PartyId.generate().value(), NOW);
        var service = new SocialGraphInteractionPolicyService(new SocialGraphRelationshipPolicyPort() {
            @Override
            public Uni<Boolean> isBlocked(TenantId scope, PartyId first, PartyId second, Instant at) {
                throw new AssertionError("unresolved identity must not query policy");
            }

            @Override
            public Uni<Boolean> isMuted(TenantId scope, PartyId viewer, PartyId author, Instant at) {
                throw new AssertionError("unresolved identity must not query policy");
            }
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        var adapter = new SocialGraphNotificationDeliveryPolicyAdapter(service, (scope, id) -> Optional.empty());

        assertThat(adapter.evaluate(notification)).isEqualTo(NotificationDeliveryPolicy.Decision.UNRESOLVED);
    }

    @Test
    void systemNotificationWithoutSourcePartyDoesNotInventAnAuthor() {
        Notification notification = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                "billing.invoice-due", "Invoice due", "Your invoice is due",
                "invoice:123", "key-3", NOW);
        var service = new SocialGraphInteractionPolicyService(new SocialGraphRelationshipPolicyPort() {
            @Override
            public Uni<Boolean> isBlocked(TenantId scope, PartyId first, PartyId second, Instant at) {
                throw new AssertionError("system notification should not query block policy");
            }

            @Override
            public Uni<Boolean> isMuted(TenantId scope, PartyId viewer, PartyId author, Instant at) {
                throw new AssertionError("system notification should not query mute policy");
            }
        }, Clock.fixed(NOW, ZoneOffset.UTC));
        var adapter = new SocialGraphNotificationDeliveryPolicyAdapter(service,
                (scope, id) -> Optional.empty());

        assertThat(adapter.evaluate(notification)).isEqualTo(NotificationDeliveryPolicy.Decision.ALLOW);
    }
}
