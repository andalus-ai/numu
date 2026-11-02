package tech.kayys.numou.workspace.adapter.notification;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.NotificationService;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.workspace.application.port.WorkspaceMentionOutboxStore.ClaimedMention;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationWorkspaceMentionDeliveryAdapterTest {
    @Test
    void persistsRecipientScopedNotificationUsingOutboxIdempotencyKey() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID page = UUID.randomUUID();
        UUID comment = UUID.randomUUID();
        String key = "workspace:comment-mention:" + comment + ":" + recipient;
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        NotificationService service = new NotificationService(repository,
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
        var adapter = new NotificationWorkspaceMentionDeliveryAdapter(service);
        ClaimedMention mention = mention(tenant, recipient, page, comment, key);

        adapter.deliver(mention);
        adapter.deliver(mention);

        assertEquals(1, repository.created.size());
        Notification stored = repository.created.getFirst();
        assertEquals(tenant, stored.tenantId());
        assertEquals(recipient, stored.recipientId());
        assertEquals("workspace.comment.mention", stored.type());
        assertEquals("Ari mentioned you in Product roadmap", stored.title());
        assertEquals("You were mentioned in a comment.", stored.body());
        assertEquals("workspace://pages/" + page + "/comments/" + comment, stored.sourceReference());
        assertEquals(key, stored.idempotencyKey());
    }

    @Test
    void rejectsMissingMentionAndDoesNotLeakCommentBody() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID page = UUID.randomUUID();
        UUID comment = UUID.randomUUID();
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        var adapter = new NotificationWorkspaceMentionDeliveryAdapter(new NotificationService(
                repository, Clock.systemUTC()));

        assertThrows(NullPointerException.class, () -> adapter.deliver(null));
        adapter.deliver(mention(tenant, recipient, page, comment,
                "workspace:comment-mention:" + comment + ":" + recipient));
        assertFalse(repository.created.getFirst().body().contains("secret"));
    }

    private static ClaimedMention mention(UUID tenant, UUID recipient, UUID page, UUID comment, String key) {
        return new ClaimedMention(UUID.randomUUID(), tenant, page, UUID.randomUUID(), comment,
                recipient, UUID.randomUUID(), "ari", "Ari", "Product roadmap", key,
                1, UUID.randomUUID(), Instant.parse("2026-11-02T00:01:00Z"));
    }

    private static final class InMemoryNotificationRepository implements NotificationRepository {
        private final List<Notification> created = new ArrayList<>();

        @Override public Notification createIfAbsent(Notification notification) {
            return created.stream().filter(existing -> existing.tenantId().equals(notification.tenantId())
                    && existing.recipientId().equals(notification.recipientId())
                    && existing.idempotencyKey().equals(notification.idempotencyKey()))
                    .findFirst().orElseGet(() -> { created.add(notification); return notification; });
        }
        @Override public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant before) {
            return List.of();
        }
        @Override public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID id) {
            return Optional.empty();
        }
        @Override public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID id, Instant readAt) {
            return Optional.empty();
        }
    }
}
