package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.domain.Notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");

    @Test
    void clampsInboxPageSizeAndCopiesResults() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository();
        NotificationService service = new NotificationService(repository, fixedClock());

        List<Notification> results = service.inbox(tenant, recipient, 500, null);

        assertEquals(NotificationService.MAX_LIMIT, repository.limit);
        assertThrows(UnsupportedOperationException.class, () -> results.add(null));
    }

    @Test
    void defaultsInvalidPageSizeAndRequiresTenantScope() {
        RecordingRepository repository = new RecordingRepository();
        NotificationService service = new NotificationService(repository, fixedClock());

        service.inbox(UUID.randomUUID(), UUID.randomUUID(), 0, null);
        assertEquals(NotificationService.DEFAULT_LIMIT, repository.limit);
        assertThrows(NullPointerException.class, () -> service.inbox(null, UUID.randomUUID(), 10, null));
    }

    @Test
    void createUsesClockAndRepositoryCanonicalResultForIdempotency() {
        UUID tenant = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        RecordingRepository repository = new RecordingRepository();
        NotificationService service = new NotificationService(repository, fixedClock());

        Notification created = service.create(new NotificationService.CreateNotification(
                tenant, recipient, "task.assigned", "Task assigned", "A task was assigned",
                "task:123", "task-assigned-123-v1"));

        assertEquals(NOW, created.createdAt());
        assertEquals("task-assigned-123-v1", repository.saved.idempotencyKey());
        assertSame(repository.saved, created);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class RecordingRepository implements NotificationRepository {
        private Notification saved;
        private int limit;

        @Override
        public Notification createIfAbsent(Notification notification) {
            if (saved == null) saved = notification;
            return saved;
        }

        @Override
        public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant beforeCreatedAt) {
            this.limit = limit;
            return saved == null ? List.of() : List.of(saved);
        }

        @Override
        public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID notificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId, Instant readAt) {
            return Optional.empty();
        }
    }
}